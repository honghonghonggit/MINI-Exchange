package com.miniexchange.api.dto;

import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderStatus;
import com.miniexchange.domain.OrderType;

import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String clientOrderId,
        OrderSide side,
        OrderType type,
        long price,
        long quantity,
        long remainingQuantity,
        OrderStatus status,
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(), o.getClientOrderId(),
                o.getSide(), o.getType(),
                o.getPrice(), o.getQuantity(),
                o.getRemainingQuantity(), o.getStatus(),
                o.getCreatedAt());
    }
}
