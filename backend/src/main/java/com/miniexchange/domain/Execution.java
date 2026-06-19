package com.miniexchange.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "executions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buy_order_id", nullable = false)
    private Order buyOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sell_order_id", nullable = false)
    private Order sellOrder;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private LocalDateTime executedAt;
}
