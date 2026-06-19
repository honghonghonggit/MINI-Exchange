package com.miniexchange.api;

import com.miniexchange.engine.MatchingEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MetricsController.class)
class MetricsControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean MatchingEngine matchingEngine;

    @Test
    void getMetrics_returnsAllFields() throws Exception {
        when(matchingEngine.lastLatencyUs()).thenReturn(12.5);
        when(matchingEngine.avgLatencyUs()).thenReturn(10.0);
        when(matchingEngine.tps()).thenReturn(1234.0);
        when(matchingEngine.openOrderCount()).thenReturn(42);

        mockMvc.perform(get("/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastLatencyUs").value(12.5))
                .andExpect(jsonPath("$.avgLatencyUs").value(10.0))
                .andExpect(jsonPath("$.tps").value(1234.0))
                .andExpect(jsonPath("$.openOrderCount").value(42));
    }
}
