package com.miniexchange.simulator.trader;

import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderType;

/**
 * 트레이더가 주문을 내보내는 출구. 실제 구현은 OrderService로 위임하지만,
 * 인터페이스로 분리해 테스트에서는 호출을 캡처하는 가짜 구현을 끼울 수 있다.
 */
@FunctionalInterface
public interface OrderGateway {
    void submit(OrderSide side, OrderType type, long price, long quantity, String tag);
}
