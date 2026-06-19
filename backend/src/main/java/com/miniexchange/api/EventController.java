package com.miniexchange.api;

import com.miniexchange.api.dto.EventLogResponse;
import com.miniexchange.service.EventLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EventController {

    private final EventLogService eventLogService;

    @GetMapping("/events")
    public ResponseEntity<List<EventLogResponse>> getRecentEvents() {
        return ResponseEntity.ok(eventLogService.getRecentEvents());
    }
}
