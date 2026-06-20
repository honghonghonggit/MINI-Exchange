package com.miniexchange.simulator.trader;

import java.util.random.RandomGenerator;

/**
 * 가상 투자자 전략. 매 tick 시장 뷰를 보고 주문을 낸다(혹은 내지 않는다).
 * 설계 결정: 무상태 전략 + 외부 주입 RNG → 시드 고정으로 결정적 단위 테스트 가능.
 */
public interface Trader {
    void act(MarketView market, OrderGateway gateway, RandomGenerator rng);

    /** 이벤트 로그에서 출처를 구분하기 위한 태그(clientOrderId 접두사). */
    String tag();
}
