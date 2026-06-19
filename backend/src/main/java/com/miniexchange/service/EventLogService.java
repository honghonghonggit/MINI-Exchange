package com.miniexchange.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniexchange.domain.EventLog;
import com.miniexchange.domain.EventLog.EventType;
import com.miniexchange.domain.Order;
import com.miniexchange.engine.MatchResult;
import com.miniexchange.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventLogService {

    private final EventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;

    @Async("persistExecutor")
    @Transactional
    public void logOrderSubmitted(Order order) {
        save(EventType.ORDER_SUBMITTED, Map.of(
                "orderId", order.getId(),
                "side", order.getSide(),
                "type", order.getType(),
                "price", order.getPrice(),
                "quantity", order.getQuantity()
        ));
    }

    @Async("persistExecutor")
    @Transactional
    public void logOrderCancelled(Order order) {
        save(EventType.ORDER_CANCELLED, Map.of("orderId", order.getId()));
    }

    @Async("persistExecutor")
    @Transactional
    public void logExecutions(List<MatchResult> results) {
        for (MatchResult r : results) {
            save(EventType.EXECUTION, Map.of(
                    "buyOrderId", r.buyOrder().getId(),
                    "sellOrderId", r.sellOrder().getId(),
                    "price", r.price(),
                    "quantity", r.quantity()
            ));
            EventType takerEvent = r.takerOrder().getRemainingQuantity() == 0
                    ? EventType.ORDER_FILLED : EventType.ORDER_PARTIAL;
            save(takerEvent, Map.of("orderId", r.takerOrder().getId(),
                    "remainingQuantity", r.takerOrder().getRemainingQuantity()));
        }
    }

    private void save(EventType type, Object payload) {
        try {
            eventLogRepository.save(EventLog.builder()
                    .eventType(type)
                    .payload(objectMapper.writeValueAsString(payload))
                    .timestamp(LocalDateTime.now())
                    .build());
        } catch (JsonProcessingException e) {
            log.warn("EventLog payload 직렬화 실패: {}", e.getMessage());
        }
    }
}
