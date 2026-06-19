package com.miniexchange.api;

import com.miniexchange.api.dto.EventLogResponse;
import com.miniexchange.domain.EventLog.EventType;
import com.miniexchange.service.EventLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean EventLogService eventLogService;

    @Test
    void getEvents_returnsList() throws Exception {
        List<EventLogResponse> events = List.of(
                new EventLogResponse(1L, EventType.EXECUTION, "{\"price\":50000}", LocalDateTime.now()));
        when(eventLogService.getRecentEvents()).thenReturn(events);

        mockMvc.perform(get("/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("EXECUTION"))
                .andExpect(jsonPath("$[0].id").value(1));
    }
}
