package com.miniexchange.simulator.trader;

import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderType;

import java.util.random.RandomGenerator;

/**
 * 모멘텀 트레이더: 최근 추세를 따라간다. 상승 추세면 시장가 매수, 하락 추세면 시장가 매도.
 * 추세를 증폭시켜 가격 변동(때로는 급변동)을 만든다 → VI 트리거의 재료가 된다.
 */
public class MomentumTrader implements Trader {

    private final long trendThreshold; // 이 이상 추세가 잡히면 따라붙는다
    private final int maxQty;

    public MomentumTrader() {
        this(100L, 3);
    }

    public MomentumTrader(long trendThreshold, int maxQty) {
        this.trendThreshold = trendThreshold;
        this.maxQty = maxQty;
    }

    @Override
    public void act(MarketView m, OrderGateway g, RandomGenerator rng) {
        long trend = m.trend();
        if (Math.abs(trend) < trendThreshold) return; // 추세 약하면 관망

        long qty = 1 + rng.nextInt(maxQty);
        OrderSide side = trend > 0 ? OrderSide.BUY : OrderSide.SELL;
        g.submit(side, OrderType.MARKET, 0L, qty, tag());
    }

    @Override
    public String tag() {
        return "MOMENTUM";
    }
}
