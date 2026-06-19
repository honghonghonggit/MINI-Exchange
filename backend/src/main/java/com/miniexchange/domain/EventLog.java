package com.miniexchange.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public enum EventType {
        ORDER_SUBMITTED,
        ORDER_CANCELLED,
        ORDER_FILLED,
        ORDER_PARTIAL,
        EXECUTION
    }
}
