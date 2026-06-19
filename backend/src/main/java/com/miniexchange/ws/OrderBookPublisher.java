package com.miniexchange.ws;

import com.miniexchange.engine.MatchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 체결 결과를 WebSocket 구독자에게 브로드캐스트한다.
 * Phase 1: 스텁 (로깅만). WebSocket 설정 단계에서 실구현.
 */
@Slf4j
@Component
public class OrderBookPublisher {

    public void broadcast(List<MatchResult> results) {
        log.debug("Matched {} execution(s) — WebSocket broadcast pending implementation", results.size());
    }
}
