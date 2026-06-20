package com.miniexchange.engine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VolatilityGuard 단위 테스트. now(epoch ms)를 직접 주입해 시계 없이 결정적으로 검증한다.
 */
class VolatilityGuardTest {

    // threshold 2.5%, cooldown 5s, refInterval 8s
    private VolatilityGuard guard() {
        return new VolatilityGuard(0.025, 5_000, 8_000);
    }

    @Test
    void 기준가_미설정이면_트리거안함() {
        assertThat(guard().wouldTrigger(99_999)).isFalse();
    }

    @Test
    void 임계_초과면_트리거_미만이면_안함() {
        VolatilityGuard g = guard();
        g.maybeUpdateReference(50_000, 0);

        assertThat(g.wouldTrigger(51_500)).isTrue();   // +3.0% > 2.5%
        assertThat(g.wouldTrigger(48_500)).isTrue();   // -3.0% > 2.5%
        assertThat(g.wouldTrigger(51_000)).isFalse();  // +2.0% < 2.5%
        assertThat(g.wouldTrigger(50_000)).isFalse();  // 0%
    }

    @Test
    void trigger하면_정지되고_haltUntil이_설정된다() {
        VolatilityGuard g = guard();
        g.trigger(1_000);

        assertThat(g.halted()).isTrue();
        assertThat(g.haltUntil()).isEqualTo(6_000); // 1000 + 5000
    }

    @Test
    void release하면_정지해제되고_기준가가_갱신된다() {
        VolatilityGuard g = guard();
        g.maybeUpdateReference(50_000, 0);
        g.trigger(1_000);

        g.release(53_000, 6_000);

        assertThat(g.halted()).isFalse();
        assertThat(g.referencePrice()).isEqualTo(53_000);
    }

    @Test
    void 기준가는_refInterval_경과_전엔_갱신안된다() {
        VolatilityGuard g = guard();
        g.maybeUpdateReference(50_000, 0);

        g.maybeUpdateReference(60_000, 1_000);  // 1s < 8s → 무시
        assertThat(g.referencePrice()).isEqualTo(50_000);

        g.maybeUpdateReference(60_000, 8_000);  // 8s 경과 → 갱신
        assertThat(g.referencePrice()).isEqualTo(60_000);
    }

    @Test
    void disabled_가드는_절대_트리거안함() {
        VolatilityGuard g = VolatilityGuard.disabled();
        g.maybeUpdateReference(50_000, 0);

        assertThat(g.enabled()).isFalse();
        assertThat(g.wouldTrigger(100_000)).isFalse();
    }
}
