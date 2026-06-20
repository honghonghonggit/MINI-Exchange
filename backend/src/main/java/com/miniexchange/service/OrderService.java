package com.miniexchange.service;

import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderStatus;
import com.miniexchange.domain.OrderType;
import com.miniexchange.engine.MatchingEngine;
import com.miniexchange.engine.OrderBookSnapshot;
import com.miniexchange.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 제출/취소 흐름:
 *   1. REST 스레드: Order를 DB에 저장 (DB id 확보) → 이벤트 로그 → 매칭 엔진 큐에 제출
 *   2. 매칭 스레드: OrderBook.submit() → MatchResult 생성 → WS 브로드캐스트 + persist 큐
 *   3. Persist 스레드: Execution 저장 + Order 상태 업데이트 + EventLog 기록
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MatchingEngine matchingEngine;
    private final EventLogService eventLogService;

    public Order submitOrder(OrderSide side, OrderType type, long price, long quantity) {
        return submitOrder(side, type, price, quantity, null);
    }

    /**
     * 태그 포함 주문 제출. tag는 clientOrderId 접두사로 들어가
     * 이벤트 로그/주문 원장에서 출처(예: 트레이더 유형)를 식별할 수 있게 한다.
     */
    public Order submitOrder(OrderSide side, OrderType type, long price, long quantity, String tag) {
        LocalDateTime now = LocalDateTime.now();
        String clientOrderId = (tag == null ? "" : tag + "-") + UUID.randomUUID();
        Order order = Order.builder()
                .clientOrderId(clientOrderId)
                .side(side)
                .type(type)
                .price(price)
                .quantity(quantity)
                .remainingQuantity(quantity)
                .status(OrderStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build();

        orderRepository.save(order);
        eventLogService.logOrderSubmitted(order);
        matchingEngine.submit(order);
        return order;
    }

    public boolean cancelOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .filter(o -> o.getStatus() == OrderStatus.OPEN || o.getStatus() == OrderStatus.PARTIAL)
                .map(o -> {
                    matchingEngine.cancel(orderId);
                    eventLogService.logOrderCancelled(o);
                    return true;
                })
                .orElse(false);
    }

    public OrderBookSnapshot getSnapshot() {
        return matchingEngine.snapshot();
    }
}
