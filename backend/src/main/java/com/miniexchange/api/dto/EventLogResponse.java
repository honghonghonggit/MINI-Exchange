package com.miniexchange.api.dto;

import com.miniexchange.domain.EventLog;

import java.time.LocalDateTime;

public record EventLogResponse(
        Long id,
        EventLog.EventType eventType,
        String payload,
        LocalDateTime timestamp
) {
    public static EventLogResponse from(EventLog e) {
        return new EventLogResponse(e.getId(), e.getEventType(), e.getPayload(), e.getTimestamp());
    }
}
