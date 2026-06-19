package com.miniexchange.engine;

import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderSide;

/**
 * 한 번의 체결 결과. maker(book에 있던 주문)와 taker(새로 들어온 주문)로 표현한다.
 * 매수/매도 구분은 side로부터 파생되므로 buyOrder()/sellOrder() 헬퍼로 제공한다.
 */
public record MatchResult(
        Order makerOrder,
        Order takerOrder,
        long price,
        long quantity
) {
    /** 체결의 매수 측 주문 */
    public Order buyOrder() {
        return takerOrder.getSide() == OrderSide.BUY ? takerOrder : makerOrder;
    }

    /** 체결의 매도 측 주문 */
    public Order sellOrder() {
        return takerOrder.getSide() == OrderSide.SELL ? takerOrder : makerOrder;
    }
}
