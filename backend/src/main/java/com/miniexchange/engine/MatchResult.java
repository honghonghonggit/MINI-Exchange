package com.miniexchange.engine;

import com.miniexchange.domain.Order;

public record MatchResult(
        Order makerOrder,
        Order takerOrder,
        long price,
        long quantity
) {}
