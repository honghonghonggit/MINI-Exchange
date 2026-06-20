package com.miniexchange.ws;

import com.miniexchange.engine.ViState;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * VI(변동성완화장치) 상태 전환 시 /topic/vi 로 브로드캐스트한다.
 * 프론트엔드는 이를 받아 "거래 일시정지" 배너를 표시/해제한다.
 */
@Component
@RequiredArgsConstructor
public class ViPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(ViState state) {
        messagingTemplate.convertAndSend("/topic/vi", state);
    }
}
