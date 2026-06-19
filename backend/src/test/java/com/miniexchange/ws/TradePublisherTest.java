package com.miniexchange.ws;

import com.miniexchange.api.dto.TradeResponse;
import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderSide;
import com.miniexchange.engine.MatchResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TradePublisherTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks TradePublisher tradePublisher;

    @Test
    @SuppressWarnings("unchecked")
    void broadcast_sendsTradesToTopic() {
        Order maker = Order.builder().id(1L).side(OrderSide.SELL).build();
        Order taker = Order.builder().id(2L).side(OrderSide.BUY).build();
        MatchResult result = new MatchResult(maker, taker, 50_000L, 5L);

        tradePublisher.broadcast(List.of(result));

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(topic.capture(), payload.capture());

        assertThat(topic.getValue()).isEqualTo("/topic/trades");
        List<TradeResponse> trades = (List<TradeResponse>) payload.getValue();
        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).price()).isEqualTo(50_000L);
        assertThat(trades.get(0).quantity()).isEqualTo(5L);
    }
}
