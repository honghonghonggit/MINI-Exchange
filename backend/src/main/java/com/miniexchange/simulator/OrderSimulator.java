package com.miniexchange.simulator;

import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderType;
import com.miniexchange.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * 랜덤워크 기반 가상 주문 생성기.
 * 설계 결정:
 *   - 기준가(referencePrice)가 가우시안 랜덤워크로 움직임 → 자연스러운 가격 변동
 *   - limit/market 주문을 9:1 비율로 섞어 체결이 꾸준히 발생하게 유지
 *   - 매수/매도 주문을 균등하게 생성해 오더북이 한쪽으로 쏠리지 않게 함
 *   - simulator.enabled=false 로 비활성화 가능 (테스트 환경 등)
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true", matchIfMissing = true)
public class OrderSimulator {

    private static final long BASE_PRICE    = 50_000L;  // 기준 시작가
    private static final long TICK_SIZE     = 100L;     // 최소 호가 단위
    private static final double VOLATILITY  = 0.0005;   // 랜덤워크 변동성 (0.05%)
    private static final int    MARKET_RATE = 10;       // 10회 중 1회 market 주문

    private final OrderService orderService;
    private final RandomGenerator rng = new Random();

    private long referencePrice = BASE_PRICE;
    private int tickCount = 0;

    @Scheduled(fixedDelayString = "${simulator.interval-ms:500}")
    public void tick() {
        try {
            updateReferencePrice();
            submitLimitOrders();

            // market 주문으로 체결 촉진
            if (++tickCount % MARKET_RATE == 0) {
                submitMarketOrder();
            }
        } catch (Exception e) {
            log.warn("Simulator tick error: {}", e.getMessage());
        }
    }

    /** 가우시안 랜덤워크로 기준가 갱신 */
    private void updateReferencePrice() {
        double drift = rng.nextGaussian() * VOLATILITY * referencePrice;
        referencePrice = Math.max(TICK_SIZE, referencePrice + roundToTick((long) drift));
    }

    /** 기준가 ±1~5틱 범위에 limit 매수/매도 각 1건 */
    private void submitLimitOrders() {
        long spread = (1 + rng.nextInt(3)) * TICK_SIZE;
        long qty    = 1 + rng.nextInt(5);

        // 매도: 기준가 위
        long askPrice = roundToTick(referencePrice + spread);
        orderService.submitOrder(OrderSide.SELL, OrderType.LIMIT, askPrice, qty);

        // 매수: 기준가 아래
        long bidPrice = roundToTick(referencePrice - spread);
        orderService.submitOrder(OrderSide.BUY, OrderType.LIMIT, bidPrice, qty);
    }

    /** 시장가 주문으로 기존 잔량을 체결 */
    private void submitMarketOrder() {
        long qty  = 1 + rng.nextInt(3);
        OrderSide side = rng.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
        orderService.submitOrder(side, OrderType.MARKET, 0L, qty);
        log.debug("Market {} qty={}", side, qty);
    }

    private long roundToTick(long price) {
        return Math.max(TICK_SIZE, (price / TICK_SIZE) * TICK_SIZE);
    }
}
