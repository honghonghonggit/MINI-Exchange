package com.miniexchange.simulator.trader;

import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 트레이더 전략 단위 테스트.
 * 시드 고정 RNG + 호출을 캡처하는 가짜 OrderGateway로 각 전략의 방향성 결정을 검증한다.
 */
class TraderStrategyTest {

    /** 트레이더가 낸 주문을 기록하는 가짜 게이트웨이 */
    static class CapturingGateway implements OrderGateway {
        record Call(OrderSide side, OrderType type, long price, long quantity, String tag) {}
        final List<Call> calls = new ArrayList<>();

        @Override
        public void submit(OrderSide side, OrderType type, long price, long quantity, String tag) {
            calls.add(new Call(side, type, price, quantity, tag));
        }
    }

    private final Random rng = new Random(42);

    private MarketView view(long last, long reference, List<Long> recent) {
        return new MarketView(null, null, last, reference, recent);
    }

    // --- NoiseTrader ---

    @Test
    void noiseTrader_매수매도_양면_limit를_낸다() {
        CapturingGateway g = new CapturingGateway();
        new NoiseTrader().act(view(0, 50_000, List.of()), g, rng);

        assertThat(g.calls).hasSize(2);
        assertThat(g.calls).allMatch(c -> c.type() == OrderType.LIMIT && c.tag().equals("NOISE"));
        assertThat(g.calls).extracting(CapturingGateway.Call::side)
                .containsExactlyInAnyOrder(OrderSide.BUY, OrderSide.SELL);
    }

    // --- MomentumTrader ---

    @Test
    void momentumTrader_상승추세면_시장가_매수() {
        CapturingGateway g = new CapturingGateway();
        // 추세 = 50500 - 49000 = +1500 (임계 100 초과)
        new MomentumTrader().act(view(50_500, 50_000, List.of(49_000L, 49_500L, 50_000L, 50_500L)), g, rng);

        assertThat(g.calls).hasSize(1);
        assertThat(g.calls.get(0).side()).isEqualTo(OrderSide.BUY);
        assertThat(g.calls.get(0).type()).isEqualTo(OrderType.MARKET);
    }

    @Test
    void momentumTrader_하락추세면_시장가_매도() {
        CapturingGateway g = new CapturingGateway();
        new MomentumTrader().act(view(49_000, 50_000, List.of(50_500L, 50_000L, 49_500L, 49_000L)), g, rng);

        assertThat(g.calls).hasSize(1);
        assertThat(g.calls.get(0).side()).isEqualTo(OrderSide.SELL);
        assertThat(g.calls.get(0).type()).isEqualTo(OrderType.MARKET);
    }

    @Test
    void momentumTrader_추세_약하면_관망() {
        CapturingGateway g = new CapturingGateway();
        // 추세 = 50050 - 50000 = 50 (임계 100 미만)
        new MomentumTrader().act(view(50_050, 50_000, List.of(50_000L, 50_050L)), g, rng);

        assertThat(g.calls).isEmpty();
    }

    // --- MeanReversionTrader ---

    @Test
    void meanReversionTrader_평균보다_비싸면_매도() {
        CapturingGateway g = new CapturingGateway();
        // ma = 50000, band = 250, 현재가 51000 > 50250 → 매도
        new MeanReversionTrader().act(view(51_000, 50_000, List.of(50_000L, 50_000L, 50_000L)), g, rng);

        assertThat(g.calls).hasSize(1);
        assertThat(g.calls.get(0).side()).isEqualTo(OrderSide.SELL);
        assertThat(g.calls.get(0).type()).isEqualTo(OrderType.LIMIT);
    }

    @Test
    void meanReversionTrader_평균보다_싸면_매수() {
        CapturingGateway g = new CapturingGateway();
        new MeanReversionTrader().act(view(49_000, 50_000, List.of(50_000L, 50_000L, 50_000L)), g, rng);

        assertThat(g.calls).hasSize(1);
        assertThat(g.calls.get(0).side()).isEqualTo(OrderSide.BUY);
    }

    @Test
    void meanReversionTrader_band_안이면_관망() {
        CapturingGateway g = new CapturingGateway();
        // ma = 50000, band = 250, 현재가 50100 → band 안 → 관망
        new MeanReversionTrader().act(view(50_100, 50_000, List.of(50_000L, 50_000L, 50_000L)), g, rng);

        assertThat(g.calls).isEmpty();
    }

    // --- LargeTrader ---

    @Test
    void largeTrader_확률100이면_큰_시장가_주문() {
        CapturingGateway g = new CapturingGateway();
        new LargeTrader(100, 20, 30).act(view(50_000, 50_000, List.of()), g, rng);

        assertThat(g.calls).hasSize(1);
        assertThat(g.calls.get(0).type()).isEqualTo(OrderType.MARKET);
        assertThat(g.calls.get(0).quantity()).isGreaterThanOrEqualTo(20);
        assertThat(g.calls.get(0).tag()).isEqualTo("WHALE");
    }

    @Test
    void largeTrader_확률0이면_관망() {
        CapturingGateway g = new CapturingGateway();
        new LargeTrader(0, 20, 30).act(view(50_000, 50_000, List.of()), g, rng);

        assertThat(g.calls).isEmpty();
    }
}
