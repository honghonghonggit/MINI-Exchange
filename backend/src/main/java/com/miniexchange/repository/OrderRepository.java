package com.miniexchange.repository;

import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByClientOrderId(String clientOrderId);
    List<Order> findTop50ByStatusNotOrderByCreatedAtDesc(OrderStatus status);
}
