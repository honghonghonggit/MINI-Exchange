package com.miniexchange.engine;

import com.miniexchange.domain.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * VI(변동성완화장치) 통합 테스트 — 실제 MatchingEngine(단일 스레드)로 검증.
 * 짧은 cooldown(300ms) + 큰 refInterval(기준가 고정)으로 트리거→정지→해제(일괄체결) 흐름을 본다.
 */
class MatchingEngineViTest {

    private static final AtomicLong idSeq = new AtomicLong(2000);

    @Test
    void 급변동_체결이_들어오면_정지되고_cooldown후_일괄체결으로_해제된다() throws InterruptedException {
        List<MatchResult> matches = new ArrayList<>();
        List<ViState> viStates = new ArrayList<>();

        // threshold 2%, cooldown 300ms, refInterval 매우 크게 → 기준가는 첫 체결가에 고정
        VolatilityGuard guard = new VolatilityGuard(0.02, 300, 1_000_000);
        MatchingEngine engine = new MatchingEngine(
                snapshot -> {},
                results -> { synchronized (matches) { matches.addAll(results); } },
                state -> { synchronized (viStates) { viStates.add(state); } },
                guard);

        // 1) 기준가 확립: 50,000에 체결 발생 → reference = 50,000
        engine.submit(sell(50_000, 5));
        engine.submit(buy(50_000, 5));
        await().atMost(2, SECONDS).until(() -> !matches.isEmpty());
        assertThat(matches.get(0).price()).isEqualTo(50_000L);

        // 2) 기준가에서 +6% 떨어진 53,000에 체결 발생 → 그 직후 VI 발동(정적 VI)
        engine.submit(sell(53_000, 5));   // 안착(반대편 없음)
        engine.submit(buy(53_000, 5));    // 53,000에 체결 → 밴드(±2%) 이탈 → 트리거
        await().atMost(2, SECONDS).until(() -> viStates.stream().anyMatch(ViState::halted));

        int matchesAtTrigger = matchCount(matches); // 50,000 + 53,000 체결 = 2건

        // 정지 중: 서로 교차하는 주문을 넣어도 체결되지 않고 쌓이기만 한다
        engine.submit(sell(53_000, 5));
        engine.submit(buy(53_000, 5));
        Thread.sleep(150); // 정지 중 처리되는지 잠깐 대기
        assertThat(matchCount(matches)).isEqualTo(matchesAtTrigger); // 체결 늘지 않음

        // 3) cooldown(300ms) 경과 후 해제 + 일괄 체결(uncross)로 쌓인 교차 주문이 체결됨
        await().atMost(2, SECONDS).until(() -> viStates.stream().anyMatch(s -> !s.halted()));
        await().atMost(2, SECONDS).until(() -> matchCount(matches) > matchesAtTrigger);

        // 해제 시 일괄체결이 53,000에 이뤄졌는지 확인
        synchronized (matches) {
            assertThat(matches.get(matches.size() - 1).price()).isEqualTo(53_000L);
        }

        engine.stop();
    }

    private int matchCount(List<MatchResult> matches) {
        synchronized (matches) { return matches.size(); }
    }

    // --- 헬퍼 ---

    private Order buy(long price, long qty)  { return order(OrderSide.BUY, price, qty); }
    private Order sell(long price, long qty) { return order(OrderSide.SELL, price, qty); }

    private Order order(OrderSide side, long price, long qty) {
        long id = idSeq.getAndIncrement();
        return Order.builder()
                .id(id).clientOrderId("vi-" + id)
                .side(side).type(OrderType.LIMIT)
                .price(price).quantity(qty).remainingQuantity(qty)
                .status(OrderStatus.OPEN)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }
}
