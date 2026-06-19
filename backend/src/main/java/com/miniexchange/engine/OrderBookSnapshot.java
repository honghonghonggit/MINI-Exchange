package com.miniexchange.engine;

import java.util.List;

public record OrderBookSnapshot(
        List<PriceLevel> bids,
        List<PriceLevel> asks
) {
    public record PriceLevel(long price, long quantity, int orderCount) {}
}
