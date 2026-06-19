package com.miniexchange.engine;

import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderStatus;
import com.miniexchange.domain.OrderType;

import java.util.*;

/**
 * 인메모리 오더북.
 * 설계 결정:
 *   - 매수: TreeMap(역순) → 높은 가격이 firstKey() = best bid
 *   - 매도: TreeMap(정순) → 낮은 가격이 firstKey() = best ask
 *   - 동일 가격: ArrayDeque로 FIFO 보장
 *   - 취소 O(1) 조회를 위해 orderIndex(HashMap) 별도 유지
 *   - 이 클래스는 단일 매칭 스레드에서만 접근 → 내부 동기화 없음
 */
public class OrderBook {

    private final TreeMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();
    private final Map<Long, Order> orderIndex = new HashMap<>();

    /**
     * 주문을 제출하고 발생한 체결 목록을 반환한다.
     * limit 주문은 미체결 잔량이 있으면 book에 남는다.
     * market 주문은 잔량이 남아도 book에 남지 않는다.
     */
    public List<MatchResult> submit(Order taker) {
        List<MatchResult> results = taker.getType() == OrderType.MARKET
                ? matchMarket(taker)
                : matchLimit(taker);

        if (taker.getType() == OrderType.LIMIT && taker.getRemainingQuantity() > 0) {
            enqueue(taker);
        }
        return results;
    }

    /**
     * 주문을 취소한다. book에 없으면(이미 체결/취소) false 반환.
     */
    public boolean cancel(Long orderId) {
        Order order = orderIndex.remove(orderId);
        if (order == null) return false;

        TreeMap<Long, ArrayDeque<Order>> book = bookFor(order.getSide());
        ArrayDeque<Order> level = book.get(order.getPrice());
        if (level == null) return false;

        boolean removed = level.remove(order); // 참조 동일성으로 제거 — O(n) but 레벨당 주문 수는 소수
        if (level.isEmpty()) book.remove(order.getPrice());
        if (removed) order.setStatus(OrderStatus.CANCELLED);
        return removed;
    }

    /**
     * 현재 오더북 스냅샷을 반환한다 (불변 record).
     * 매칭 스레드에서만 호출되므로 추가 동기화 불필요.
     */
    public OrderBookSnapshot snapshot() {
        List<OrderBookSnapshot.PriceLevel> bidLevels = bids.entrySet().stream()
                .map(e -> new OrderBookSnapshot.PriceLevel(
                        e.getKey(),
                        e.getValue().stream().mapToLong(Order::getRemainingQuantity).sum(),
                        e.getValue().size()))
                .toList();

        List<OrderBookSnapshot.PriceLevel> askLevels = asks.entrySet().stream()
                .map(e -> new OrderBookSnapshot.PriceLevel(
                        e.getKey(),
                        e.getValue().stream().mapToLong(Order::getRemainingQuantity).sum(),
                        e.getValue().size()))
                .toList();

        return new OrderBookSnapshot(bidLevels, askLevels);
    }

    // --- 테스트용 접근자 ---

    public int bidLevels()        { return bids.size(); }
    public int askLevels()        { return asks.size(); }
    public Long bestBidPrice()    { return bids.isEmpty() ? null : bids.firstKey(); }
    public Long bestAskPrice()    { return asks.isEmpty() ? null : asks.firstKey(); }

    // --- Private 매칭 로직 ---

    private List<MatchResult> matchLimit(Order taker) {
        List<MatchResult> results = new ArrayList<>();
        TreeMap<Long, ArrayDeque<Order>> opposite = bookFor(taker.getSide().opposite());

        while (taker.getRemainingQuantity() > 0 && !opposite.isEmpty()) {
            long bestPrice = opposite.firstKey();
            boolean crosses = taker.getSide() == OrderSide.BUY
                    ? taker.getPrice() >= bestPrice
                    : taker.getPrice() <= bestPrice;
            if (!crosses) break;

            results.addAll(drainLevel(opposite, bestPrice, taker));
        }
        return results;
    }

    private List<MatchResult> matchMarket(Order taker) {
        List<MatchResult> results = new ArrayList<>();
        TreeMap<Long, ArrayDeque<Order>> opposite = bookFor(taker.getSide().opposite());

        while (taker.getRemainingQuantity() > 0 && !opposite.isEmpty()) {
            results.addAll(drainLevel(opposite, opposite.firstKey(), taker));
        }
        if (taker.getRemainingQuantity() > 0) {
            taker.setStatus(OrderStatus.CANCELLED); // 잔량 있는 market order는 취소 처리
        }
        return results;
    }

    private List<MatchResult> drainLevel(
            TreeMap<Long, ArrayDeque<Order>> opposite, long levelPrice, Order taker) {
        List<MatchResult> results = new ArrayList<>();
        ArrayDeque<Order> level = opposite.get(levelPrice);

        while (taker.getRemainingQuantity() > 0 && !level.isEmpty()) {
            Order maker = level.peek();
            long qty = Math.min(taker.getRemainingQuantity(), maker.getRemainingQuantity());

            applyFill(taker, maker, qty);
            results.add(new MatchResult(maker, taker, levelPrice, qty));

            if (maker.getRemainingQuantity() == 0) {
                level.poll();
                orderIndex.remove(maker.getId());
            }
        }
        if (level.isEmpty()) opposite.remove(levelPrice);
        return results;
    }

    private void applyFill(Order taker, Order maker, long qty) {
        taker.setRemainingQuantity(taker.getRemainingQuantity() - qty);
        maker.setRemainingQuantity(maker.getRemainingQuantity() - qty);
        updateStatus(taker);
        updateStatus(maker);
    }

    private void updateStatus(Order order) {
        if (order.getRemainingQuantity() == 0) {
            order.setStatus(OrderStatus.FILLED);
        } else if (order.getRemainingQuantity() < order.getQuantity()) {
            order.setStatus(OrderStatus.PARTIAL);
        }
    }

    private void enqueue(Order order) {
        bookFor(order.getSide())
                .computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>())
                .offer(order);
        orderIndex.put(order.getId(), order);
    }

    private TreeMap<Long, ArrayDeque<Order>> bookFor(OrderSide side) {
        return side == OrderSide.BUY ? bids : asks;
    }
}
