package com.miniexchange.simulator.trader;

import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderType;

import java.util.random.RandomGenerator;

/**
 * 대형 투자자(고래): 낮은 확률로 여러 호가 레벨을 휩쓰는 큰 시장가 주문을 낸다.
 * 한 번에 가격을 크게 점프시켜 급변동을 만든다 → VI 발동을 유발하고 차트에서 눈에 띈다.
 */
public class LargeTrader implements Trader {

    private final int probabilityPct; // 매 tick 발동 확률(%)
    private final int minQty;
    private final int maxExtraQty;

    public LargeTrader() {
        this(4, 20, 30);
    }

    public LargeTrader(int probabilityPct, int minQty, int maxExtraQty) {
        this.probabilityPct = probabilityPct;
        this.minQty = minQty;
        this.maxExtraQty = maxExtraQty;
    }

    @Override
    public void act(MarketView m, OrderGateway g, RandomGenerator rng) {
        if (rng.nextInt(100) >= probabilityPct) return; // 대부분의 tick은 관망

        long qty = minQty + rng.nextInt(maxExtraQty);
        OrderSide side = rng.nextBoolean() ? OrderSide.BUY : OrderSide.SELL;
        g.submit(side, OrderType.MARKET, 0L, qty, tag());
    }

    @Override
    public String tag() {
        return "WHALE";
    }
}
