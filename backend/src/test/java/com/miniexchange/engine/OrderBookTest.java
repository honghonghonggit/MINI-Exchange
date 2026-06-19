package com.miniexchange.engine;

import com.miniexchange.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class OrderBookTest {

    private OrderBook book;
    private static final AtomicLong idSeq = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        book = new OrderBook();
    }

    // === 구조 / Depth 테스트 ===

    @Test
    void emptyBook_hasNoBidsOrAsks() {
        assertThat(book.bidLevels()).isZero();
        assertThat(book.askLevels()).isZero();
        assertThat(book.bestBidPrice()).isNull();
        assertThat(book.bestAskPrice()).isNull();
    }

    @Test
    void addLimitBid_appearsInBids() {
        book.submit(limitBuy(50_000L, 10));
        assertThat(book.bidLevels()).isEqualTo(1);
        assertThat(book.bestBidPrice()).isEqualTo(50_000L);
    }

    @Test
    void addLimitAsk_appearsInAsks() {
        book.submit(limitSell(51_000L, 10));
        assertThat(book.askLevels()).isEqualTo(1);
        assertThat(book.bestAskPrice()).isEqualTo(51_000L);
    }

    @Test
    void bestBid_isHighestBidPrice() {
        book.submit(limitBuy(49_000L, 5));
        book.submit(limitBuy(50_000L, 5));
        book.submit(limitBuy(48_000L, 5));
        assertThat(book.bestBidPrice()).isEqualTo(50_000L);
    }

    @Test
    void bestAsk_isLowestAskPrice() {
        book.submit(limitSell(52_000L, 5));
        book.submit(limitSell(51_000L, 5));
        book.submit(limitSell(53_000L, 5));
        assertThat(book.bestAskPrice()).isEqualTo(51_000L);
    }

    // === 매칭 테스트 ===

    @Test
    void limitBuyAboveAsk_matchesAtMakerPrice() {
        book.submit(limitSell(50_000L, 10));            // maker
        List<MatchResult> results = book.submit(limitBuy(51_000L, 10)); // taker

        assertThat(results).hasSize(1);
        assertThat(results.get(0).price()).isEqualTo(50_000L);  // maker 가격으로 체결
        assertThat(results.get(0).quantity()).isEqualTo(10L);
        assertThat(book.askLevels()).isZero();
    }

    @Test
    void limitSellBelowBid_matchesAtMakerPrice() {
        book.submit(limitBuy(51_000L, 10));              // maker
        List<MatchResult> results = book.submit(limitSell(50_000L, 10)); // taker

        assertThat(results).hasSize(1);
        assertThat(results.get(0).price()).isEqualTo(51_000L);  // maker 가격으로 체결
        assertThat(results.get(0).quantity()).isEqualTo(10L);
        assertThat(book.bidLevels()).isZero();
    }

    @Test
    void noMatch_whenBidBelowAsk() {
        book.submit(limitSell(51_000L, 10));
        List<MatchResult> results = book.submit(limitBuy(50_000L, 10));

        assertThat(results).isEmpty();
        assertThat(book.bidLevels()).isEqualTo(1);
        assertThat(book.askLevels()).isEqualTo(1);
    }

    @Test
    void partialFill_takerRemainsInBook() {
        book.submit(limitSell(50_000L, 5));               // maker: 5
        List<MatchResult> results = book.submit(limitBuy(50_000L, 10)); // taker: 10

        assertThat(results).hasSize(1);
        assertThat(results.get(0).quantity()).isEqualTo(5L);
        // taker 잔량 5 → bid에 남아있어야 함
        assertThat(book.bidLevels()).isEqualTo(1);
        assertThat(book.bestBidPrice()).isEqualTo(50_000L);
        assertThat(book.askLevels()).isZero();
    }

    @Test
    void partialFill_makerRemainsInBook() {
        book.submit(limitSell(50_000L, 10));              // maker: 10
        List<MatchResult> results = book.submit(limitBuy(50_000L, 5));  // taker: 5

        assertThat(results).hasSize(1);
        assertThat(results.get(0).quantity()).isEqualTo(5L);
        // taker 완전체결 → bid 없음, maker 잔량 5 → ask에 남음
        assertThat(book.bidLevels()).isZero();
        assertThat(book.askLevels()).isEqualTo(1);
        assertThat(book.bestAskPrice()).isEqualTo(50_000L);
    }

    @Test
    void fifo_samePriceMatchesEarlierOrderFirst() {
        Order first = limitSell(50_000L, 5);
        Order second = limitSell(50_000L, 5);
        book.submit(first);
        book.submit(second);

        List<MatchResult> results = book.submit(limitBuy(50_000L, 5));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).makerOrder()).isSameAs(first);  // 먼저 들어온 주문이 체결
    }

    @Test
    void marketBuy_matchesBestAsk() {
        book.submit(limitSell(52_000L, 5));
        book.submit(limitSell(51_000L, 5));  // best ask

        List<MatchResult> results = book.submit(marketBuy(5));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).price()).isEqualTo(51_000L);
    }

    @Test
    void marketSell_matchesBestBid() {
        book.submit(limitBuy(49_000L, 5));
        book.submit(limitBuy(50_000L, 5));  // best bid

        List<MatchResult> results = book.submit(marketSell(5));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).price()).isEqualTo(50_000L);
    }

    @Test
    void marketOrder_doesNotRestInBook_whenUnfilled() {
        List<MatchResult> results = book.submit(marketBuy(10)); // 매도 없음

        assertThat(results).isEmpty();
        assertThat(book.bidLevels()).isZero(); // market order는 book에 남지 않음
    }

    @Test
    void matchSweepsMultiplePriceLevels() {
        book.submit(limitSell(50_000L, 5));
        book.submit(limitSell(51_000L, 5));

        // 51000에 매수 → 두 레벨 모두 소진
        List<MatchResult> results = book.submit(limitBuy(51_000L, 10));

        assertThat(results).hasSize(2);
        assertThat(book.askLevels()).isZero();
    }

    // === 취소 테스트 ===

    @Test
    void cancelOrder_removesFromBook() {
        Order order = limitBuy(50_000L, 10);
        book.submit(order);

        boolean cancelled = book.cancel(order.getId());

        assertThat(cancelled).isTrue();
        assertThat(book.bidLevels()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelNonexistentOrder_returnsFalse() {
        assertThat(book.cancel(999L)).isFalse();
    }

    @Test
    void cancelFilledOrder_returnsFalse() {
        Order sell = limitSell(50_000L, 10);
        Order buy  = limitBuy(50_000L, 10);
        book.submit(sell);
        book.submit(buy);

        // sell이 완전체결되어 book에서 제거됨
        assertThat(book.cancel(sell.getId())).isFalse();
    }

    // === 상태 검증 테스트 ===

    @Test
    void filledOrder_hasFilledStatus() {
        Order sell = limitSell(50_000L, 10);
        Order buy  = limitBuy(50_000L, 10);
        book.submit(sell);
        book.submit(buy);

        assertThat(sell.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void partiallyFilledOrder_hasPartialStatus() {
        Order sell = limitSell(50_000L, 3);
        Order buy  = limitBuy(50_000L, 10);
        book.submit(sell);
        book.submit(buy);

        assertThat(sell.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(buy.getStatus()).isEqualTo(OrderStatus.PARTIAL);
    }

    // --- 테스트 헬퍼 ---

    private Order limitBuy(long price, long qty) {
        long id = idSeq.getAndIncrement();
        return Order.builder()
                .id(id)
                .clientOrderId("c-" + id)
                .side(OrderSide.BUY)
                .type(OrderType.LIMIT)
                .price(price)
                .quantity(qty)
                .remainingQuantity(qty)
                .status(OrderStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Order limitSell(long price, long qty) {
        long id = idSeq.getAndIncrement();
        return Order.builder()
                .id(id)
                .clientOrderId("c-" + id)
                .side(OrderSide.SELL)
                .type(OrderType.LIMIT)
                .price(price)
                .quantity(qty)
                .remainingQuantity(qty)
                .status(OrderStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Order marketBuy(long qty) {
        long id = idSeq.getAndIncrement();
        return Order.builder()
                .id(id)
                .clientOrderId("c-" + id)
                .side(OrderSide.BUY)
                .type(OrderType.MARKET)
                .price(0L)
                .quantity(qty)
                .remainingQuantity(qty)
                .status(OrderStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private Order marketSell(long qty) {
        long id = idSeq.getAndIncrement();
        return Order.builder()
                .id(id)
                .clientOrderId("c-" + id)
                .side(OrderSide.SELL)
                .type(OrderType.MARKET)
                .price(0L)
                .quantity(qty)
                .remainingQuantity(qty)
                .status(OrderStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
