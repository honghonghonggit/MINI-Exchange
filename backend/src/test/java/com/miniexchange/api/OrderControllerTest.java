package com.miniexchange.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniexchange.api.dto.OrderRequest;
import com.miniexchange.api.dto.TradeResponse;
import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderStatus;
import com.miniexchange.domain.OrderType;
import com.miniexchange.engine.OrderBookSnapshot;
import com.miniexchange.service.OrderService;
import com.miniexchange.service.TradeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean OrderService orderService;
    @MockBean TradeService tradeService;

    @Test
    void postOrder_validRequest_returns201() throws Exception {
        Order saved = Order.builder()
                .id(1L).clientOrderId("abc")
                .side(OrderSide.BUY).type(OrderType.LIMIT)
                .price(50_000L).quantity(10L).remainingQuantity(10L)
                .status(OrderStatus.OPEN).createdAt(LocalDateTime.now())
                .build();
        when(orderService.submitOrder(any(), any(), anyLong(), anyLong())).thenReturn(saved);

        OrderRequest req = new OrderRequest(OrderSide.BUY, OrderType.LIMIT, 50_000L, 10L);
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void postOrder_invalidRequest_returns400() throws Exception {
        String body = """
                {"side":"BUY","type":"LIMIT","price":50000,"quantity":0}
                """;
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteOrder_existingOrder_returns204() throws Exception {
        when(orderService.cancelOrder(1L)).thenReturn(true);

        mockMvc.perform(delete("/orders/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteOrder_nonExistent_returns404() throws Exception {
        when(orderService.cancelOrder(99L)).thenReturn(false);

        mockMvc.perform(delete("/orders/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOrderbook_returnsSnapshot() throws Exception {
        OrderBookSnapshot snap = new OrderBookSnapshot(
                List.of(new OrderBookSnapshot.PriceLevel(50_000L, 10L, 1)),
                List.of(new OrderBookSnapshot.PriceLevel(51_000L, 5L, 1)));
        when(orderService.getSnapshot()).thenReturn(snap);

        mockMvc.perform(get("/orderbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bids[0].price").value(50000))
                .andExpect(jsonPath("$.asks[0].price").value(51000));
    }

    @Test
    void getTrades_returnsList() throws Exception {
        List<TradeResponse> trades = List.of(
                new TradeResponse(1L, 50_000L, 5L, LocalDateTime.now()));
        when(tradeService.getRecentTrades()).thenReturn(trades);

        mockMvc.perform(get("/trades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(50000))
                .andExpect(jsonPath("$[0].quantity").value(5));
    }
}
