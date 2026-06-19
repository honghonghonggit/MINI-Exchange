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
 *   1. REST 스레드: Order를 DB에 저장 (DB id 확보) → 매칭 엔진 큐에 제출
 *   2. 매칭 스레드: OrderBook.submit() → MatchResult 생성 → WS 브로드캐스트 + persist 큐
 *   3. Persist 스레드: Execution 저장 + Order 상태 업데이트
 *
 * 취소:
 *   - DB에서 주문 조회 → 이미 체결/취소된 주문은 false 반환
 *   - CANCEL 커맨드를 매칭 큐에 추가 (단일 스레드가 순서대로 처리)
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MatchingEngine matchingEngine;

    public Order submitOrder(OrderSide side, OrderType type, long price, long quantity) {
        LocalDateTime now = LocalDateTime.now();
        Order order = Order.builder()
                .clientOrderId(UUID.randomUUID().toString())
                .side(side)
                .type(type)
                .price(price)
                .quantity(quantity)
                .remainingQuantity(quantity)
                .status(OrderStatus.OPEN)
                .createdAt(now)
                .updatedAt(now)
                .build();

        orderRepository.save(order);    // 커밋 → id 확정
        matchingEngine.submit(order);   // 매칭 큐에 추가 (논블로킹)
        return order;
    }

    public boolean cancelOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .filter(o -> o.getStatus() == OrderStatus.OPEN || o.getStatus() == OrderStatus.PARTIAL)
                .map(o -> {
                    matchingEngine.cancel(orderId);
                    return true;
                })
                .orElse(false);
    }

    public OrderBookSnapshot getSnapshot() {
        return matchingEngine.snapshot();
    }
}
