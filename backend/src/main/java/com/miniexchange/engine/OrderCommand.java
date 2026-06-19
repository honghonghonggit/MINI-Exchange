package com.miniexchange.engine;

import com.miniexchange.domain.Order;

public record OrderCommand(Type type, Order order, Long cancelOrderId) {

    public enum Type { SUBMIT, CANCEL }

    public static OrderCommand submit(Order order) {
        return new OrderCommand(Type.SUBMIT, order, null);
    }

    public static OrderCommand cancel(Long orderId) {
        return new OrderCommand(Type.CANCEL, null, orderId);
    }
}
