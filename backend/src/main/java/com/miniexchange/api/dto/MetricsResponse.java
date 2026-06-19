package com.miniexchange.api.dto;

public record MetricsResponse(
        double lastLatencyUs,
        double avgLatencyUs,
        double tps,
        int openOrderCount
) {}
