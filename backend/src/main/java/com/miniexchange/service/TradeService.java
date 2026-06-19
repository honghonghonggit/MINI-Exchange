package com.miniexchange.service;

import com.miniexchange.api.dto.TradeResponse;
import com.miniexchange.repository.ExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TradeService {

    private final ExecutionRepository executionRepository;

    public List<TradeResponse> getRecentTrades() {
        return executionRepository.findTop50ByOrderByExecutedAtDesc()
                .stream()
                .map(TradeResponse::from)
                .toList();
    }
}
