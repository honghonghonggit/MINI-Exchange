package com.miniexchange.integration;

import com.miniexchange.api.dto.ReplayResult;
import com.miniexchange.domain.EventLog.EventType;
import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderType;
import com.miniexchange.repository.EventLogRepository;
import com.miniexchange.service.OrderService;
import com.miniexchange.service.ReplayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * 통합 테스트: 라이브로 발생시킨 주문/체결을 event_logs만으로 리플레이해 재구성하고,
 * 재구성 체결이 원본과 정확히 일치하는지(결정성) 확인한다.
 * 소액 가격대(50,000 근처)라 VI는 발동하지 않으므로 순수 가격-시간 우선 재구성과 원본이 일치한다.
 */
@SpringBootTest
class ReplayIntegrationTest {

    @Autowired OrderService orderService;
    @Autowired ReplayService replayService;
    @Autowired EventLogRepository eventLogRepository;

    @Test
    void 이벤트로그를_리플레이하면_원본_체결과_정확히_일치한다() {
        eventLogRepository.deleteAll();

        // 부분체결·복수 체결을 포함하도록 여러 주문 투입 (VI 미발동 범위)
        orderService.submitOrder(OrderSide.SELL, OrderType.LIMIT, 50_000L, 5L);
        orderService.submitOrder(OrderSide.SELL, OrderType.LIMIT, 50_100L, 5L);
        orderService.submitOrder(OrderSide.BUY, OrderType.LIMIT, 50_000L, 3L);   // 부분체결 3
        orderService.submitOrder(OrderSide.BUY, OrderType.LIMIT, 50_100L, 7L);   // 2(@50000)+5(@50100)

        // 비동기 persist 완료 대기: EXECUTION 3건이 모두 기록될 때까지
        await().atMost(5, SECONDS).until(() ->
                eventLogRepository.findAll().stream()
                        .filter(e -> e.getEventType() == EventType.EXECUTION).count() == 3);

        ReplayResult result = replayService.replay();

        System.out.println("\n===== 리플레이 결과 =====\n" + result + "\n========================");

        assertThat(result.matched()).isTrue();
        assertThat(result.regeneratedExecutions()).isEqualTo(result.recordedExecutions());
        assertThat(result.regeneratedQuantity()).isEqualTo(result.recordedQuantity());
        assertThat(result.regeneratedExecutions()).isEqualTo(3);
        assertThat(result.regeneratedQuantity()).isEqualTo(10L); // 3 + 2 + 5
        assertThat(result.inputEvents()).isEqualTo(4);           // 제출 4건(취소 없음)
    }
}
