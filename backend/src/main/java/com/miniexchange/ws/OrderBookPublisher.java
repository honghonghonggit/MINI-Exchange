package com.miniexchange.ws;

import com.miniexchange.engine.OrderBookSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 오더북 변경 시마다 WebSocket 구독자에게 full snapshot을 브로드캐스트한다.
 * 설계 결정:
 *   - Phase 1: delta 대신 full snapshot 전송 — 구현 단순, 클라이언트 상태 관리 불필요
 *   - 토픽: /topic/orderbook
 *   - SimpMessagingTemplate은 thread-safe — 매칭 스레드에서 직접 호출 가능
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderBookPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(OrderBookSnapshot snapshot) {
        messagingTemplate.convertAndSend("/topic/orderbook", snapshot);
        log.debug("Broadcast: {} bids, {} asks", snapshot.bids().size(), snapshot.asks().size());
    }
}
