package com.miniexchange.api;

import com.miniexchange.api.dto.MetricsResponse;
import com.miniexchange.engine.MatchingEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MetricsController {

    private final MatchingEngine matchingEngine;

    @GetMapping("/metrics")
    public ResponseEntity<MetricsResponse> getMetrics() {
        return ResponseEntity.ok(new MetricsResponse(
                matchingEngine.lastLatencyUs(),
                matchingEngine.avgLatencyUs(),
                matchingEngine.tps(),
                matchingEngine.openOrderCount()
        ));
    }
}
