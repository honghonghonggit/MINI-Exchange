package com.miniexchange.simulator.trader;

import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderType;

import java.util.random.RandomGenerator;

/**
 * 노이즈 트레이더: 기준가 ±1~3틱에 매수/매도 limit를 양면으로 깔아 유동성/호가 depth를 공급한다.
 * 방향성 없이 양면을 채우므로 오더북이 한쪽으로 쏠리지 않게 유지하는 역할.
 */
public class NoiseTrader implements Trader {

    private static final long TICK = 100L;
    private final int maxSpreadTicks;
    private final int maxQty;

    public NoiseTrader() {
        this(3, 5);
    }

    public NoiseTrader(int maxSpreadTicks, int maxQty) {
        this.maxSpreadTicks = maxSpreadTicks;
        this.maxQty = maxQty;
    }

    @Override
    public void act(MarketView m, OrderGateway g, RandomGenerator rng) {
        long ref = m.referencePrice();
        long spread = (1 + rng.nextInt(maxSpreadTicks)) * TICK;
        long qty = 1 + rng.nextInt(maxQty);

        g.submit(OrderSide.SELL, OrderType.LIMIT, roundTick(ref + spread), qty, tag());
        g.submit(OrderSide.BUY, OrderType.LIMIT, roundTick(ref - spread), qty, tag());
    }

    @Override
    public String tag() {
        return "NOISE";
    }

    private long roundTick(long price) {
        return Math.max(TICK, (price / TICK) * TICK);
    }
}
