package com.miniexchange.api;

import com.miniexchange.api.dto.OrderRequest;
import com.miniexchange.api.dto.OrderResponse;
import com.miniexchange.api.dto.TradeResponse;
import com.miniexchange.engine.OrderBookSnapshot;
import com.miniexchange.service.OrderService;
import com.miniexchange.service.TradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;
    private final TradeService tradeService;

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> submitOrder(@Valid @RequestBody OrderRequest req) {
        var order = orderService.submitOrder(req.side(), req.type(), req.price(), req.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id) {
        boolean cancelled = orderService.cancelOrder(id);
        return cancelled
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/orderbook")
    public ResponseEntity<OrderBookSnapshot> getOrderBook() {
        return ResponseEntity.ok(orderService.getSnapshot());
    }

    @GetMapping("/trades")
    public ResponseEntity<List<TradeResponse>> getRecentTrades() {
        return ResponseEntity.ok(tradeService.getRecentTrades());
    }
}
