package com.miniexchange.service;

import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderStatus;
import com.miniexchange.domain.OrderType;
import com.miniexchange.engine.MatchingEngine;
import com.miniexchange.engine.OrderBookSnapshot;
import com.miniexchange.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock MatchingEngine matchingEngine;
    @InjectMocks OrderService orderService;

    @Test
    void submitOrder_savesToDbAndSubmitsToEngine() {
        Order order = orderService.submitOrder(OrderSide.BUY, OrderType.LIMIT, 50_000L, 10L);

        verify(orderRepository).save(any(Order.class));
        verify(matchingEngine).submit(any(Order.class));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(order.getRemainingQuantity()).isEqualTo(10L);
        assertThat(order.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(order.getClientOrderId()).isNotBlank();
    }

    @Test
    void cancelOrder_openOrder_submitsCancelAndReturnsTrue() {
        Order open = Order.builder().id(1L).status(OrderStatus.OPEN).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(open));

        boolean result = orderService.cancelOrder(1L);

        assertThat(result).isTrue();
        verify(matchingEngine).cancel(1L);
    }

    @Test
    void cancelOrder_partialOrder_submitsCancelAndReturnsTrue() {
        Order partial = Order.builder().id(2L).status(OrderStatus.PARTIAL).build();
        when(orderRepository.findById(2L)).thenReturn(Optional.of(partial));

        assertThat(orderService.cancelOrder(2L)).isTrue();
        verify(matchingEngine).cancel(2L);
    }

    @Test
    void cancelOrder_nonExistentId_returnsFalse() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(orderService.cancelOrder(99L)).isFalse();
        verify(matchingEngine, never()).cancel(any());
    }

    @Test
    void cancelOrder_filledOrder_returnsFalse() {
        Order filled = Order.builder().id(3L).status(OrderStatus.FILLED).build();
        when(orderRepository.findById(3L)).thenReturn(Optional.of(filled));

        assertThat(orderService.cancelOrder(3L)).isFalse();
        verify(matchingEngine, never()).cancel(any());
    }

    @Test
    void cancelOrder_cancelledOrder_returnsFalse() {
        Order cancelled = Order.builder().id(4L).status(OrderStatus.CANCELLED).build();
        when(orderRepository.findById(4L)).thenReturn(Optional.of(cancelled));

        assertThat(orderService.cancelOrder(4L)).isFalse();
        verify(matchingEngine, never()).cancel(any());
    }

    @Test
    void getSnapshot_delegatesToEngine() {
        OrderBookSnapshot expected = new OrderBookSnapshot(List.of(), List.of());
        when(matchingEngine.snapshot()).thenReturn(expected);

        assertThat(orderService.getSnapshot()).isSameAs(expected);
    }
}
