package com.miniexchange.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniexchange.domain.EventLog;
import com.miniexchange.domain.EventLog.EventType;
import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderStatus;
import com.miniexchange.domain.OrderType;
import com.miniexchange.engine.MatchResult;
import com.miniexchange.repository.EventLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventLogServiceTest {

    @Mock EventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private EventLogService service() {
        return new EventLogService(eventLogRepository, objectMapper);
    }

    @Test
    void logOrderSubmitted_savesEventWithSerializedPayload() {
        Order order = Order.builder()
                .id(1L).side(OrderSide.BUY).type(OrderType.LIMIT)
                .price(50_000L).quantity(10L).remainingQuantity(10L)
                .status(OrderStatus.OPEN).build();

        service().logOrderSubmitted(order);

        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository).save(captor.capture());
        EventLog saved = captor.getValue();

        assertThat(saved.getEventType()).isEqualTo(EventType.ORDER_SUBMITTED);
        assertThat(saved.getPayload()).contains("\"orderId\":1").contains("\"price\":50000");
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void logOrderCancelled_savesCancelEvent() {
        Order order = Order.builder().id(7L).build();

        service().logOrderCancelled(order);

        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(EventType.ORDER_CANCELLED);
        assertThat(captor.getValue().getPayload()).contains("\"orderId\":7");
    }

    @Test
    void logExecutions_savesExecutionAndTakerStatusEvents() {
        Order maker = Order.builder().id(1L).side(OrderSide.SELL).remainingQuantity(0L).build();
        Order taker = Order.builder().id(2L).side(OrderSide.BUY).remainingQuantity(0L).build();
        MatchResult result = new MatchResult(maker, taker, 50_000L, 5L);

        service().logExecutions(List.of(result));

        // EXECUTION 이벤트 1건 + taker FILLED 이벤트 1건 = 2건 저장
        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository, times(2)).save(captor.capture());

        List<EventLog> saved = captor.getAllValues();
        assertThat(saved).extracting(EventLog::getEventType)
                .containsExactly(EventType.EXECUTION, EventType.ORDER_FILLED);
        assertThat(saved.get(0).getPayload())
                .contains("\"buyOrderId\":2").contains("\"sellOrderId\":1");
    }

    @Test
    void logExecutions_partialTaker_logsPartialEvent() {
        Order maker = Order.builder().id(1L).side(OrderSide.SELL).remainingQuantity(0L).build();
        Order taker = Order.builder().id(2L).side(OrderSide.BUY).remainingQuantity(3L).build();
        MatchResult result = new MatchResult(maker, taker, 50_000L, 5L);

        service().logExecutions(List.of(result));

        ArgumentCaptor<EventLog> captor = ArgumentCaptor.forClass(EventLog.class);
        verify(eventLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(EventLog::getEventType)
                .containsExactly(EventType.EXECUTION, EventType.ORDER_PARTIAL);
    }

    @Test
    void getRecentEvents_mapsToResponse() {
        EventLog log = EventLog.builder()
                .id(1L).eventType(EventType.EXECUTION).payload("{}")
                .timestamp(java.time.LocalDateTime.now()).build();
        when(eventLogRepository.findTop100ByOrderByTimestampDesc()).thenReturn(List.of(log));

        var events = service().getRecentEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(EventType.EXECUTION);
    }
}
