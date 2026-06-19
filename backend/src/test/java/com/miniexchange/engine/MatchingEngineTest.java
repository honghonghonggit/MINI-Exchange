package com.miniexchange.engine;

import com.miniexchange.domain.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class MatchingEngineTest {

    private static final AtomicLong idSeq = new AtomicLong(1000);

    @Test
    void submit_triggersMatchCallback_whenOrdersCross() throws InterruptedException {
        List<MatchResult> captured = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        MatchingEngine engine = new MatchingEngine(
                snapshot -> {},   // WS 브로드캐스트 무시
                results -> {
                    captured.addAll(results);
                    latch.countDown();
                });

        engine.submit(limitSell(50_000L, 10));
        engine.submit(limitBuy(50_000L, 10));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured).hasSize(1);
        assertThat(captured.get(0).price()).isEqualTo(50_000L);
        assertThat(captured.get(0).quantity()).isEqualTo(10L);

        engine.stop();
    }

    @Test
    void noMatch_matchCallbackNotFired() throws InterruptedException {
        List<MatchResult> captured = new ArrayList<>();
        MatchingEngine engine = new MatchingEngine(snapshot -> {}, captured::addAll);

        engine.submit(limitSell(51_000L, 10));
        engine.submit(limitBuy(50_000L, 10));

        Thread.sleep(200);
        assertThat(captured).isEmpty();

        engine.stop();
    }

    @Test
    void onOrderBookChanged_firedAfterEveryCommand() throws InterruptedException {
        List<OrderBookSnapshot> snapshots = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(2);

        MatchingEngine engine = new MatchingEngine(
                snapshot -> { snapshots.add(snapshot); latch.countDown(); },
                results -> {});

        engine.submit(limitBuy(50_000L, 5));
        engine.submit(limitSell(51_000L, 5));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(snapshots).hasSize(2);

        engine.stop();
    }

    @Test
    void snapshot_reflectsBookStateAfterSubmit() throws InterruptedException {
        MatchingEngine engine = new MatchingEngine(snapshot -> {}, results -> {});

        engine.submit(limitBuy(50_000L, 5));
        engine.submit(limitSell(51_000L, 5));

        Thread.sleep(200);

        OrderBookSnapshot snap = engine.snapshot();
        assertThat(snap.bids()).hasSize(1);
        assertThat(snap.asks()).hasSize(1);
        assertThat(snap.bids().get(0).price()).isEqualTo(50_000L);
        assertThat(snap.asks().get(0).price()).isEqualTo(51_000L);

        engine.stop();
    }

    // --- 테스트 헬퍼 ---

    private Order limitBuy(long price, long qty) {
        long id = idSeq.getAndIncrement();
        return Order.builder()
                .id(id).clientOrderId("me-" + id)
                .side(OrderSide.BUY).type(OrderType.LIMIT)
                .price(price).quantity(qty).remainingQuantity(qty)
                .status(OrderStatus.OPEN)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private Order limitSell(long price, long qty) {
        long id = idSeq.getAndIncrement();
        return Order.builder()
                .id(id).clientOrderId("me-" + id)
                .side(OrderSide.SELL).type(OrderType.LIMIT)
                .price(price).quantity(qty).remainingQuantity(qty)
                .status(OrderStatus.OPEN)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
