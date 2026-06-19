package com.miniexchange.engine;

import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderStatus;
import com.miniexchange.domain.OrderType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 성능 테스트: 1만 / 10만 / 50만 건 주문 처리 시 매칭 레이턴시·TPS 측정
 * - OrderBook.submit()을 직접 호출 (DB/WS 오버헤드 제외, 순수 매칭 엔진 성능)
 * - 가격 교차가 발생하도록 교대로 BUY/SELL 제출
 */
class MatchingEnginePerformanceTest {

    @Test
    void performance_10K() throws InterruptedException {
        runBenchmark(10_000, "10K");
    }

    @Test
    void performance_100K() throws InterruptedException {
        runBenchmark(100_000, "100K");
    }

    @Test
    void performance_500K() throws InterruptedException {
        runBenchmark(500_000, "500K");
    }

    private void runBenchmark(int orderCount, String label) throws InterruptedException {
        List<Long> latenciesNs = new ArrayList<>(orderCount);
        AtomicLong matchCount = new AtomicLong(0);

        CountDownLatch done = new CountDownLatch(1);

        MatchingEngine engine = new MatchingEngine(
                snapshot -> {},
                results -> matchCount.addAndGet(results.size())
        );

        // 워밍업 (JIT 컴파일 안정화)
        for (int i = 0; i < 1000; i++) {
            engine.submit(buildOrder(i, i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL));
        }
        Thread.sleep(500);

        // 실제 측정
        long wallStart = System.nanoTime();
        for (int i = 0; i < orderCount; i++) {
            Order order = buildOrder(i, i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL);
            long t0 = System.nanoTime();
            engine.submit(order);
            long t1 = System.nanoTime();
            latenciesNs.add(t1 - t0);  // offer() 레이턴시 (큐 삽입 시간)
        }

        // 큐가 소진될 때까지 대기
        Thread.sleep(3000);
        long wallEnd = System.nanoTime();
        engine.stop();

        double wallSec = (wallEnd - wallStart) / 1e9;
        double tps = orderCount / wallSec;

        LongSummaryStatistics stats = latenciesNs.stream()
                .mapToLong(Long::longValue)
                .summaryStatistics();

        List<Long> sorted = latenciesNs.stream().sorted().toList();
        long p99 = sorted.get((int)(orderCount * 0.99));
        long p999 = sorted.get((int)(orderCount * 0.999));

        System.out.printf("%n===== 성능 테스트: %s =====%n", label);
        System.out.printf("총 주문 수     : %,d%n", orderCount);
        System.out.printf("총 체결 건수   : %,d%n", matchCount.get());
        System.out.printf("처리 시간      : %.2f 초%n", wallSec);
        System.out.printf("TPS            : %,.0f 건/초%n", tps);
        System.out.printf("offer() 레이턴시 (주문 제출)%n");
        System.out.printf("  평균         : %.2f µs%n", stats.getAverage() / 1000);
        System.out.printf("  최소         : %.2f µs%n", stats.getMin() / 1000.0);
        System.out.printf("  최대         : %.2f µs%n", stats.getMax() / 1000.0);
        System.out.printf("  p99          : %.2f µs%n", p99 / 1000.0);
        System.out.printf("  p99.9        : %.2f µs%n", p999 / 1000.0);
        System.out.println("===============================");
    }

    private long idSeq = 1;

    private Order buildOrder(int i, OrderSide side) {
        long price = 50_000L + (i % 10) * 100L;
        return Order.builder()
                .id(idSeq++)
                .side(side)
                .type(OrderType.LIMIT)
                .price(price)
                .quantity(1L)
                .remainingQuantity(1L)
                .status(OrderStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
