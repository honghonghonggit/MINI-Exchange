package com.miniexchange.api.dto;

import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderRequest(
        @NotNull OrderSide side,
        @NotNull OrderType type,
        @Min(0) long price,     // MARKET 주문은 0 허용
        @Min(1) long quantity
) {}
