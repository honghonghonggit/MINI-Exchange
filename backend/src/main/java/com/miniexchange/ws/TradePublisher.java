package com.miniexchange.ws;

import com.miniexchange.api.dto.TradeResponse;
import com.miniexchange.engine.MatchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 체결 발생 시 /topic/trades 로 브로드캐스트한다.
 * 체결 테이프(실시간 스크롤) 및 가격 차트용.
 */
@Component
@RequiredArgsConstructor
public class TradePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(List<MatchResult> results) {
        List<TradeResponse> trades = results.stream()
                .map(r -> new TradeResponse(null, r.price(), r.quantity(), LocalDateTime.now()))
                .toList();
        messagingTemplate.convertAndSend("/topic/trades", trades);
    }
}
