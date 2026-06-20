package com.miniexchange.simulator;

import com.miniexchange.engine.MatchingEngine;
import com.miniexchange.engine.OrderBookSnapshot;
import com.miniexchange.service.OrderService;
import com.miniexchange.simulator.trader.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * 다중 전략 가상 주문 생성기.
 * 설계 결정 (Phase 3):
 *   - 단일 랜덤워크 → 여러 트레이더 전략(노이즈·모멘텀·평균회귀·대형)의 조합으로 확장.
 *   - 매 tick 공유 시장상태(기준가 앵커 + 최근 체결가 윈도우)를 갱신하고,
 *     읽기 전용 MarketView를 만들어 각 트레이더에게 전달 → 트레이더는 무상태 전략으로 분리.
 *   - 기준가는 느린 가우시안 랜덤워크로 떠다니되, 실제 체결가가 있으면 그쪽으로 끌어당겨
 *     시장이 자기 체결 결과에 반응하게 한다(모멘텀/평균회귀가 반응할 실제 신호 제공).
 *   - simulator.enabled=false 로 비활성화 가능 (테스트 환경 등).
 */
@Slf4j
@Component
@EnableScheduling
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true", matchIfMissing = true)
public class OrderSimulator {

    private static final long BASE_PRICE   = 50_000L;
    private static final long TICK_SIZE    = 100L;
    private static final double VOLATILITY = 0.0005; // 기준가 랜덤워크 변동성 (0.05%)
    private static final int PRICE_WINDOW  = 20;      // 추세/이동평균용 최근 체결가 개수

    private final OrderService orderService;
    private final MatchingEngine engine;
    private final RandomGenerator rng = new Random();

    private final List<Trader> traders = List.of(
            new NoiseTrader(),
            new MomentumTrader(),
            new MeanReversionTrader(),
            new LargeTrader()
    );

    /** 트레이더 → OrderService 위임 게이트웨이 (태그를 clientOrderId 접두사로 전달) */
    private final OrderGateway gateway;

    private final Deque<Long> recentPrices = new ArrayDeque<>();
    private long referencePrice = BASE_PRICE;

    public OrderSimulator(OrderService orderService, MatchingEngine engine) {
        this.orderService = orderService;
        this.engine = engine;
        this.gateway = (side, type, price, qty, tag) ->
                orderService.submitOrder(side, type, price, qty, tag);
    }

    @Scheduled(fixedDelayString = "${simulator.interval-ms:500}")
    public void tick() {
        try {
            MarketView view = buildMarketView();
            for (Trader trader : traders) {
                trader.act(view, gateway, rng);
            }
        } catch (Exception e) {
            log.warn("Simulator tick error: {}", e.getMessage());
        }
    }

    /** 공유 시장상태를 갱신하고 트레이더에게 줄 읽기 전용 뷰를 만든다. */
    private MarketView buildMarketView() {
        long lastTrade = engine.lastTradePrice();
        if (lastTrade > 0) recordPrice(lastTrade);
        updateReferencePrice(lastTrade);

        OrderBookSnapshot snap = engine.snapshot();
        Long bestBid = snap.bids().isEmpty() ? null : snap.bids().get(0).price();
        Long bestAsk = snap.asks().isEmpty() ? null : snap.asks().get(0).price();

        return new MarketView(bestBid, bestAsk, lastTrade, referencePrice, List.copyOf(recentPrices));
    }

    /** 기준가: 가우시안 랜덤워크로 떠다니되 실제 체결가 쪽으로 절반 끌어당김. */
    private void updateReferencePrice(long lastTrade) {
        double drift = rng.nextGaussian() * VOLATILITY * referencePrice;
        long next = referencePrice + roundToTick((long) drift);
        if (lastTrade > 0) {
            next = (next + lastTrade) / 2; // 체결가에 수렴
        }
        referencePrice = Math.max(TICK_SIZE, roundToTick(next));
    }

    private void recordPrice(long price) {
        if (!recentPrices.isEmpty() && recentPrices.peekLast() == price) return; // 동일가 중복 제거
        recentPrices.addLast(price);
        while (recentPrices.size() > PRICE_WINDOW) recentPrices.pollFirst();
    }

    private long roundToTick(long price) {
        return (price / TICK_SIZE) * TICK_SIZE;
    }
}
