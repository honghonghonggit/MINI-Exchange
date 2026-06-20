package com.miniexchange.simulator.trader;

import java.util.List;

/**
 * 트레이더에게 제공되는 읽기 전용 시장 뷰.
 * 설계 결정: 트레이더는 OrderBook/엔진 내부에 직접 접근하지 않고 이 불변 스냅샷만 본다
 * → 전략 로직을 순수 함수처럼 단위 테스트할 수 있다.
 *
 * @param bestBid       최우선 매수호가 (없으면 null)
 * @param bestAsk       최우선 매도호가 (없으면 null)
 * @param lastTradePrice 마지막 체결가 (0 = 아직 체결 없음)
 * @param referencePrice 시뮬레이터 기준가 (체결이 없을 때의 앵커)
 * @param recentPrices   최근 체결가 윈도우 (오래된→최신 순), 추세/이동평균 계산용
 */
public record MarketView(
        Long bestBid,
        Long bestAsk,
        long lastTradePrice,
        long referencePrice,
        List<Long> recentPrices
) {
    /** 체결가가 있으면 그것을, 없으면 기준가를 현재가로 본다. */
    public long currentPrice() {
        return lastTradePrice > 0 ? lastTradePrice : referencePrice;
    }

    /** 최근 윈도우의 단순 이동평균. 비어 있으면 기준가. */
    public long movingAverage() {
        if (recentPrices.isEmpty()) return referencePrice;
        long sum = 0;
        for (long p : recentPrices) sum += p;
        return sum / recentPrices.size();
    }

    /** 최근 추세 = 윈도우 마지막 - 처음. 양수면 상승, 음수면 하락. */
    public long trend() {
        if (recentPrices.size() < 2) return 0;
        return recentPrices.get(recentPrices.size() - 1) - recentPrices.get(0);
    }
}
