package com.miniexchange.simulator.trader;

import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderType;

import java.util.random.RandomGenerator;

/**
 * 평균회귀 트레이더: 가격이 이동평균에서 band 이상 벗어나면 되돌림에 베팅한다.
 * 평균보다 비싸면 limit 매도, 싸면 limit 매수 → 가격을 평균으로 끌어당기는 역할(모멘텀의 반대).
 */
public class MeanReversionTrader implements Trader {

    private final int bandDenominator; // band = ma / bandDenominator (예: 200 → ±0.5%)
    private final int maxQty;

    public MeanReversionTrader() {
        this(200, 4);
    }

    public MeanReversionTrader(int bandDenominator, int maxQty) {
        this.bandDenominator = bandDenominator;
        this.maxQty = maxQty;
    }

    @Override
    public void act(MarketView m, OrderGateway g, RandomGenerator rng) {
        long ma = m.movingAverage();
        long price = m.currentPrice();
        long band = Math.max(TICK, ma / bandDenominator);
        long qty = 1 + rng.nextInt(maxQty);

        if (price > ma + band) {
            g.submit(OrderSide.SELL, OrderType.LIMIT, roundTick(price), qty, tag()); // 비싸다 → 매도
        } else if (price < ma - band) {
            g.submit(OrderSide.BUY, OrderType.LIMIT, roundTick(price), qty, tag());  // 싸다 → 매수
        }
    }

    @Override
    public String tag() {
        return "REVERT";
    }

    private static final long TICK = 100L;

    private long roundTick(long price) {
        return Math.max(TICK, (price / TICK) * TICK);
    }
}
