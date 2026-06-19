package com.miniexchange.engine;

import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderSide;
import com.miniexchange.domain.OrderStatus;
import com.miniexchange.domain.OrderType;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 성능 테스트: 1만 / 10만 / 50만 건 주문 처리 벤치마크.
 *
 * (A) 매칭 레이턴시 + 메모리 — OrderBook.submit()을 직접 호출하여 매칭 알고리즘 자체의
 *     처리 시간을 주문별로 측정 (스레드 스케줄링 노이즈 배제). avg/max/p99/p99.9.
 * (B) 실측 TPS + PersistQueue 백프레셔 — 실제 MatchingEngine(단일 스레드) +
 *     MatchingEngineConfig와 동일한 ThreadPoolTaskExecutor(core 1, queue 10000)로
 *     비동기 저장 큐가 밀리는지(high-water mark / rejection), 큐가 끝까지 소진되는지 측정.
 *     TPS는 하드코딩 대기 없이 "큐가 완전히 소진된 시점"까지의 실제 처리 시간으로 계산.
 */
class MatchingEnginePerformanceTest {

    @Test
    void perf_10K() throws InterruptedException {
        matchingLatencyAndMemory(10_000, "10K");
        throughputAndPersistQueue(10_000, "10K");
    }

    @Test
    void perf_100K() throws InterruptedException {
        matchingLatencyAndMemory(100_000, "100K");
        throughputAndPersistQueue(100_000, "100K");
    }

    @Test
    void perf_500K() throws InterruptedException {
        matchingLatencyAndMemory(500_000, "500K");
        throughputAndPersistQueue(500_000, "500K");
    }

    // ===== (A) 매칭 레이턴시 + 메모리 =====
    private void matchingLatencyAndMemory(int n, String label) {
        OrderBook book = new OrderBook();
        long[] latencies = new long[n];

        // 워밍업 (JIT)
        OrderBook warm = new OrderBook();
        for (int i = 0; i < 2000; i++) warm.submit(buildOrder(i, side(i)));

        long heapBefore = usedHeapBytes();

        long idSeq = 1;
        for (int i = 0; i < n; i++) {
            Order order = buildOrder((int)(idSeq), side(i));
            order.setId(idSeq++);
            long t0 = System.nanoTime();
            book.submit(order);          // ← 순수 매칭 처리 (가격교차 탐색 + 체결 + 잔량 등록)
            latencies[i] = System.nanoTime() - t0;
        }

        long heapAfter = usedHeapBytes();

        java.util.Arrays.sort(latencies);
        double avg = 0; long max = 0;
        for (long l : latencies) { avg += l; if (l > max) max = l; }
        avg /= n;
        long p99 = latencies[(int)(n * 0.99)];
        long p999 = latencies[(int)(n * 0.999)];

        System.out.printf("%n##### (A) 매칭 레이턴시 + 메모리 [%s] #####%n", label);
        System.out.printf("주문 수            : %,d%n", n);
        System.out.printf("매칭 레이턴시 평균 : %.3f us%n", avg / 1000.0);
        System.out.printf("매칭 레이턴시 최대 : %.3f us%n", max / 1000.0);
        System.out.printf("매칭 레이턴시 p99  : %.3f us%n", p99 / 1000.0);
        System.out.printf("매칭 레이턴시 p99.9: %.3f us%n", p999 / 1000.0);
        System.out.printf("순수 매칭 처리량   : %,.0f orders/sec%n", n / (sum(latencies) / 1e9));
        System.out.printf("잔류 미체결 주문   : %,d%n", book.openOrderCount());
        System.out.printf("힙 사용량 before   : %,d KB%n", heapBefore / 1024);
        System.out.printf("힙 사용량 after    : %,d KB (증가 %,d KB)%n",
                heapAfter / 1024, (heapAfter - heapBefore) / 1024);
    }

    // ===== (B) 실측 TPS + PersistQueue 백프레셔 =====
    private void throughputAndPersistQueue(int n, String label) throws InterruptedException {
        // MatchingEngineConfig와 동일한 설정의 persist 스레드풀
        ThreadPoolTaskExecutor persistExecutor = new ThreadPoolTaskExecutor();
        persistExecutor.setCorePoolSize(1);
        persistExecutor.setMaxPoolSize(2);
        persistExecutor.setQueueCapacity(10_000);
        persistExecutor.setThreadNamePrefix("persist-bench-");
        persistExecutor.initialize();

        AtomicInteger persistSubmitted = new AtomicInteger(0);
        AtomicInteger persistCompleted = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        AtomicInteger queueHighWater = new AtomicInteger(0);
        AtomicLong matchCount = new AtomicLong(0);

        MatchingEngine engine = new MatchingEngine(
                snapshot -> {},
                results -> {
                    matchCount.addAndGet(results.size());
                    persistSubmitted.incrementAndGet();
                    // 실제 PersistService.save()와 동일하게 비동기 큐에 적재
                    try {
                        persistExecutor.execute(() -> {
                            // 저장 작업 시뮬레이션 (Execution 객체 구성 비용 모사)
                            int sink = 0;
                            for (MatchResult r : results) sink += (int) r.quantity();
                            if (sink < 0) System.out.print("");
                            persistCompleted.incrementAndGet();
                        });
                        int qsize = persistExecutor.getThreadPoolExecutor().getQueue().size();
                        queueHighWater.accumulateAndGet(qsize, Math::max);
                    } catch (RejectedExecutionException e) {
                        rejectedCount.incrementAndGet();
                    }
                }
        );

        long warmup = 2000;
        for (int i = 0; i < warmup; i++) engine.submit(buildOrder(i, side(i)));
        while (engine.processedCommandCount() < warmup) Thread.sleep(5);
        long baseProcessed = engine.processedCommandCount();

        long heapBefore = usedHeapBytes();
        long start = System.nanoTime();

        long idSeq = warmup + 1;
        for (int i = 0; i < n; i++) {
            Order order = buildOrder((int) idSeq, side(i));
            order.setId(idSeq++);
            engine.submit(order);
        }

        // 하드코딩 대기 없음 — 매칭 스레드가 모든 명령을 처리 완료할 때까지 폴링
        while (engine.processedCommandCount() - baseProcessed < n) Thread.sleep(1);
        long matchingDoneNs = System.nanoTime() - start;

        // persist 큐가 완전히 소진될 때까지 대기 (제출 == 완료+거부, 또는 30초 타임아웃)
        long persistWaitStart = System.nanoTime();
        while (persistCompleted.get() + rejectedCount.get() < persistSubmitted.get()
                && (System.nanoTime() - persistWaitStart) < 30_000_000_000L) {
            Thread.sleep(2);
        }
        long persistDrainNs = System.nanoTime() - persistWaitStart;
        long heapAfter = usedHeapBytes();

        engine.stop();
        persistExecutor.shutdown();

        double matchingSec = matchingDoneNs / 1e9;
        double tps = n / matchingSec;

        System.out.printf("%n##### (B) 실측 TPS + PersistQueue [%s] #####%n", label);
        System.out.printf("주문 수              : %,d%n", n);
        System.out.printf("체결 건수            : %,d%n", matchCount.get());
        System.out.printf("매칭 완료 시간       : %.3f sec (큐 소진까지, 하드코딩 대기 없음)%n", matchingSec);
        System.out.printf("실측 TPS             : %,.0f orders/sec%n", tps);
        System.out.printf("persist 큐 최대 적체 : %,d / 10000 (capacity)%n", queueHighWater.get());
        System.out.printf("persist 처리 완료    : %,d 건%n", persistCompleted.get());
        System.out.printf("persist 거부(reject) : %,d 건%n", rejectedCount.get());
        System.out.printf("persist task 제출    : %,d 건%n", persistSubmitted.get());
        System.out.printf("persist 큐 소진 시간 : %.3f sec%n", persistDrainNs / 1e9);
        System.out.printf("힙 증가              : %,d KB%n", (heapAfter - heapBefore) / 1024);
        System.out.println();
    }

    // --- helpers ---
    private static long sum(long[] a) { long s = 0; for (long x : a) s += x; return s; }

    private OrderSide side(int i) { return i % 2 == 0 ? OrderSide.BUY : OrderSide.SELL; }

    private long usedHeapBytes() {
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private Order buildOrder(int i, OrderSide s) {
        long price = 50_000L + (i % 10) * 100L;
        return Order.builder()
                .id((long) i)
                .side(s)
                .type(OrderType.LIMIT)
                .price(price)
                .quantity(1L)
                .remainingQuantity(1L)
                .status(OrderStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
