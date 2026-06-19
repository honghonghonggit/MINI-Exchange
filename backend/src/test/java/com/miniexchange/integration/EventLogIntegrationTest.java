package com.miniexchange.integration;

import com.miniexchange.domain.EventLog;
import com.miniexchange.domain.EventLog.EventType;
import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderType;
import com.miniexchange.repository.EventLogRepository;
import com.miniexchange.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * 통합 테스트: 주문 제출 → 체결 → event_logs 테이블에 실제로 기록되는지 DB에서 직접 확인.
 * - simulator.enabled=false (test resources) 이므로 이 테스트가 만든 이벤트만 존재
 * - 비동기 저장(@Async persistExecutor)이므로 Awaitility로 폴링
 */
@SpringBootTest
class EventLogIntegrationTest {

    @Autowired OrderService orderService;
    @Autowired EventLogRepository eventLogRepository;

    @Test
    void 주문제출과_체결이_event_logs에_타임스탬프와_함께_기록된다() {
        eventLogRepository.deleteAll();
        LocalDateTime testStart = LocalDateTime.now();

        // SELL 5개 → BUY 5개 (가격 교차 → 체결 발생)
        orderService.submitOrder(OrderSide.SELL, OrderType.LIMIT, 50_000L, 5L);
        orderService.submitOrder(OrderSide.BUY, OrderType.LIMIT, 50_000L, 5L);

        // 비동기 persist 완료 대기: EXECUTION 이벤트가 DB에 나타날 때까지
        await().atMost(5, SECONDS).until(() ->
                eventLogRepository.findAll().stream()
                        .anyMatch(e -> e.getEventType() == EventType.EXECUTION));

        List<EventLog> logs = eventLogRepository.findAll();

        // 1) 주문 제출 이벤트 2건 + 체결 이벤트 존재
        assertThat(logs).extracting(EventLog::getEventType)
                .contains(EventType.ORDER_SUBMITTED, EventType.EXECUTION);

        // 2) 모든 이벤트에 타임스탬프가 기록됨 (테스트 시작 이후)
        assertThat(logs).allSatisfy(e -> {
            assertThat(e.getTimestamp()).isNotNull();
            assertThat(e.getTimestamp()).isAfterOrEqualTo(testStart.minusSeconds(1));
            assertThat(e.getPayload()).isNotBlank();
        });

        // 3) 콘솔에 실제 DB row 출력 (육안 확인용)
        System.out.println("\n===== event_logs 테이블 실제 내용 =====");
        logs.forEach(e -> System.out.printf("[id=%d] %-16s %s  @ %s%n",
                e.getId(), e.getEventType(), e.getPayload(), e.getTimestamp()));
        System.out.println("=======================================");
    }
}
