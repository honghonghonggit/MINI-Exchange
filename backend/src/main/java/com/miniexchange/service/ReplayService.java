package com.miniexchange.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniexchange.api.dto.ReplayResult;
import com.miniexchange.domain.*;
import com.miniexchange.engine.MatchResult;
import com.miniexchange.engine.OrderBook;
import com.miniexchange.engine.OrderBookSnapshot;
import com.miniexchange.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 이벤트 리플레이.
 * 설계 결정:
 *   - event_logs의 주문 입력(ORDER_SUBMITTED/ORDER_CANCELLED)만 발생 순서대로 꺼내
 *     완전히 새로운 OrderBook에 가격-시간 우선으로 다시 매칭한다 → 라이브 상태는 건드리지 않음.
 *   - 단일 매칭 스레드 + 결정적 매칭이므로, "같은 입력을 같은 순서로 넣으면 같은 체결이 나온다".
 *     이를 원본 EXECUTION 기록과 대조해 검증한다(이벤트 소싱의 사후 복원 가능성 증명).
 *   - 한계(정직): 본 재구성은 순수 가격-시간 우선이라, 라이브에서 VI(변동성완화장치)가
 *     매칭을 지연시킨 구간이 있으면 그만큼 원본과 차이가 날 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayService {

    private final EventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ReplayResult replay() {
        List<EventLog> events = eventLogRepository.findAllByOrderByTimestampAscIdAsc();

        OrderBook book = new OrderBook();
        List<MatchResult> regenerated = new ArrayList<>();
        int inputEvents = 0;
        int recordedExecutions = 0;
        long recordedQuantity = 0;

        for (EventLog e : events) {
            switch (e.getEventType()) {
                case ORDER_SUBMITTED -> {
                    inputEvents++;
                    regenerated.addAll(book.submit(toOrder(e)));
                }
                case ORDER_CANCELLED -> {
                    inputEvents++;
                    book.cancel(parse(e).get("orderId").asLong());
                }
                case EXECUTION -> {
                    recordedExecutions++;
                    recordedQuantity += parse(e).get("quantity").asLong();
                }
                default -> { /* ORDER_FILLED/PARTIAL, VI_* 는 입력이 아니므로 무시 */ }
            }
        }

        long regeneratedQuantity = regenerated.stream().mapToLong(MatchResult::quantity).sum();
        boolean matched = regenerated.size() == recordedExecutions
                && regeneratedQuantity == recordedQuantity;

        OrderBookSnapshot snap = book.snapshot();
        log.info("리플레이: 입력 {}건 → 체결 {}건(원본 {}건), 일치={}",
                inputEvents, regenerated.size(), recordedExecutions, matched);

        return new ReplayResult(
                inputEvents, regenerated.size(), regeneratedQuantity,
                recordedExecutions, recordedQuantity, matched,
                snap.bids().size(), snap.asks().size());
    }

    private Order toOrder(EventLog e) {
        JsonNode p = parse(e);
        long qty = p.get("quantity").asLong();
        return Order.builder()
                .id(p.get("orderId").asLong())
                .clientOrderId("replay-" + p.get("orderId").asLong())
                .side(OrderSide.valueOf(p.get("side").asText()))
                .type(OrderType.valueOf(p.get("type").asText()))
                .price(p.get("price").asLong())
                .quantity(qty)
                .remainingQuantity(qty)
                .status(OrderStatus.OPEN)
                .createdAt(e.getTimestamp())
                .updatedAt(e.getTimestamp())
                .build();
    }

    private JsonNode parse(EventLog e) {
        try {
            return objectMapper.readTree(e.getPayload());
        } catch (Exception ex) {
            throw new IllegalStateException("이벤트 payload 파싱 실패: " + e.getPayload(), ex);
        }
    }
}
