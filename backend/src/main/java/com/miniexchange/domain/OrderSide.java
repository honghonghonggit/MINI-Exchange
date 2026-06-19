package com.miniexchange.domain;

public enum OrderSide {
    BUY, SELL;

    public OrderSide opposite() {
        return this == BUY ? SELL : BUY;
    }
}
