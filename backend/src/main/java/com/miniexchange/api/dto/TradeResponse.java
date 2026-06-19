package com.miniexchange.api.dto;

import com.miniexchange.domain.Execution;

import java.time.LocalDateTime;

public record TradeResponse(
        Long id,
        long price,
        long quantity,
        LocalDateTime executedAt
) {
    public static TradeResponse from(Execution e) {
        return new TradeResponse(e.getId(), e.getPrice(), e.getQuantity(), e.getExecutedAt());
    }
}
