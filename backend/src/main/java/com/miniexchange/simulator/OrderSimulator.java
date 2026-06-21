package com.miniexchange.simulator;

import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderType;
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

    private static final long BASE_PRICE     = 50_000L;
    private static final long TICK_SIZE      = 100L;
    private static final double VOLATILITY   = 0.0015; // 기준가 랜덤워크 변동성 (0.15%/tick)
    private static final int PRICE_WINDOW    = 20;      // 추세/이동평균용 최근 체결가 개수
    private static final int MAX_OPEN_ORDERS = 60;      // 오더북을 얇게 유지(메모리·변동성)
    private static final int MAX_CANCELS_PER_TICK = 20;

    private final OrderService orderService;
    private final MatchingEngine engine;
    private final RandomGenerator rng = new Random();

    private final List<Trader> traders = List.of(
            new NoiseTrader(20, 5),        // ±1~20틱(±2%) → 먼 가격대에도 호가 존재
            new MomentumTrader(),
            new MeanReversionTrader(),
            new LargeTrader(4, 100, 150)   // 100~250주 대형 → 가끔 한쪽을 쓸어 급변동 유발
    );

    /** 트레이더 → OrderService 위임 게이트웨이 (태그 전달 + 안착 주문 id 추적) */
    private final OrderGateway gateway;

    private final Deque<Long> recentPrices = new ArrayDeque<>();
    private final Deque<Long> liveOrderIds = new ArrayDeque<>(); // 만료 취소용 안착 주문 id
    private long referencePrice = BASE_PRICE;

    public OrderSimulator(OrderService orderService, MatchingEngine engine) {
        this.orderService = orderService;
        this.engine = engine;
        this.gateway = (side, type, price, qty, tag) -> {
            Order o = orderService.submitOrder(side, type, price, qty, tag);
            if (type == OrderType.LIMIT) liveOrderIds.addLast(o.getId());
        };
    }

    @Scheduled(fixedDelayString = "${simulator.interval-ms:500}")
    public void tick() {
        try {
            MarketView view = buildMarketView();
            for (Trader trader : traders) {
                trader.act(view, gateway, rng);
            }
            pruneOldOrders();
        } catch (Exception e) {
            log.warn("Simulator tick error: {}", e.getMessage());
        }
    }

    /**
     * 오더북이 상한을 넘으면 가장 오래된 안착 주문부터 취소해 책을 얇게 유지한다.
     * 설계 결정: 실시장처럼 주문이 영원히 남지 않게 해 (1) 메모리/DB 무한 증가를 막고
     * (2) 대형 주문이 가격을 실제로 움직일 수 있게(→ VI 발동 가능) 깊이를 제한한다.
     */
    private void pruneOldOrders() {
        int attempts = 0;
        while (engine.openOrderCount() > MAX_OPEN_ORDERS
                && !liveOrderIds.isEmpty()
                && attempts++ < MAX_CANCELS_PER_TICK) {
            orderService.cancelOrder(liveOrderIds.pollFirst()); // 이미 체결/취소면 false → 무시
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

    /**
     * 기준가: 가우시안 랜덤워크로 떠다니되 실제 체결가 쪽으로 약하게 끌어당긴다.
     * 설계 결정: 드리프트를 틱으로 내림(roundToTick)하면 한 걸음(±0.15%≈75원)이 1틱(100원)보다
     *   작아 거의 항상 0으로 사라져 가격이 고정된다 → 드리프트는 누적시키고(틱 정렬은 트레이더가
     *   주문 낼 때 수행) 기준가 자체는 틱에 맞추지 않는다. 체결가 수렴 가중치도 0.5→0.25로 낮춰
     *   추세가 한두 tick에 곧바로 평탄화되지 않고 살아남게 한다.
     */
    private void updateReferencePrice(long lastTrade) {
        double drift = rng.nextGaussian() * VOLATILITY * referencePrice;
        long next = referencePrice + Math.round(drift);
        if (lastTrade > 0) {
            next = Math.round(next * 0.75 + lastTrade * 0.25); // 체결가에 약하게 수렴
        }
        referencePrice = Math.max(TICK_SIZE, next);
    }

    private void recordPrice(long price) {
        if (!recentPrices.isEmpty() && recentPrices.peekLast() == price) return; // 동일가 중복 제거
        recentPrices.addLast(price);
        while (recentPrices.size() > PRICE_WINDOW) recentPrices.pollFirst();
    }
}
