package com.miniexchange.engine;

import com.miniexchange.domain.Order;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * 단일 매칭 스레드 + 커맨드 큐 패턴.
 * 설계 결정:
 *   - 외부 스레드들은 commandQueue에 명령을 넣고 즉시 반환
 *   - matchingThread 혼자 OrderBook을 읽고 씀 → lock 없이 race condition 원천 차단
 *   - onOrderBookChanged: 매 명령 처리 후 항상 호출 → WebSocket 브로드캐스트
 *   - onMatch: 체결이 발생한 경우에만 호출 → 비동기 DB persist + WS 체결 이벤트
 *   - lastSnapshot: volatile + 불변 record → REST 스레드가 안전하게 읽음
 *   - 메트릭: AtomicLong으로 레이턴시·TPS 추적 (lock-free)
 */
@Slf4j
public class MatchingEngine {

    private final LinkedBlockingQueue<OrderCommand> commandQueue = new LinkedBlockingQueue<>();
    private final OrderBook orderBook = new OrderBook();
    private final Consumer<OrderBookSnapshot> onOrderBookChanged;
    private final Consumer<List<MatchResult>> onMatch;
    private final Thread matchingThread;

    private volatile OrderBookSnapshot lastSnapshot = new OrderBookSnapshot(List.of(), List.of());
    private volatile long lastTradePrice = 0L;

    // 메트릭 (단위: nanoseconds)
    private final AtomicLong lastLatencyNs = new AtomicLong(0);
    private final AtomicLong totalLatencyNs = new AtomicLong(0);
    private final LongAdder commandCount = new LongAdder();

    // TPS: 1초 슬라이딩 윈도우
    private final LongAdder matchCountInWindow = new LongAdder();
    private volatile long windowStartMs = System.currentTimeMillis();
    private volatile double lastTps = 0.0;

    public MatchingEngine(Consumer<OrderBookSnapshot> onOrderBookChanged,
                          Consumer<List<MatchResult>> onMatch) {
        this.onOrderBookChanged = onOrderBookChanged;
        this.onMatch = onMatch;
        this.matchingThread = new Thread(this::loop, "matching-thread");
        this.matchingThread.setDaemon(true);
        this.matchingThread.start();
    }

    public void submit(Order order) {
        commandQueue.offer(OrderCommand.submit(order));
    }

    public void cancel(Long orderId) {
        commandQueue.offer(OrderCommand.cancel(orderId));
    }

    public OrderBookSnapshot snapshot() {
        return lastSnapshot;
    }

    /** 마지막 체결가 (0 = 아직 체결 없음). 시뮬레이터 트레이더가 시장을 읽는 데 사용. */
    public long lastTradePrice() {
        return lastTradePrice;
    }

    public void stop() {
        matchingThread.interrupt();
    }

    // --- 메트릭 접근자 (REST 스레드에서 읽음) ---

    /** 마지막 명령 처리 레이턴시 (µs) */
    public double lastLatencyUs() {
        return lastLatencyNs.get() / 1_000.0;
    }

    /** 누적 평균 레이턴시 (µs) */
    public double avgLatencyUs() {
        long count = commandCount.sum();
        return count == 0 ? 0.0 : totalLatencyNs.get() / 1_000.0 / count;
    }

    /** 최근 1초 체결 TPS */
    public double tps() {
        refreshTpsWindow();
        return lastTps;
    }

    /** 현재 미체결 주문 수 */
    public int openOrderCount() {
        return orderBook.openOrderCount();
    }

    /** 매칭 스레드가 지금까지 처리 완료한 명령 수 (큐 소진 판정용) */
    public long processedCommandCount() {
        return commandCount.sum();
    }

    // ---

    private void loop() {
        log.info("Matching thread started");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                OrderCommand cmd = commandQueue.take();
                long start = System.nanoTime();

                List<MatchResult> results = List.of();
                switch (cmd.type()) {
                    case SUBMIT -> results = orderBook.submit(cmd.order());
                    case CANCEL -> orderBook.cancel(cmd.cancelOrderId());
                }

                long elapsed = System.nanoTime() - start;
                lastLatencyNs.set(elapsed);
                totalLatencyNs.addAndGet(elapsed);
                commandCount.increment();

                lastSnapshot = orderBook.snapshot();
                onOrderBookChanged.accept(lastSnapshot);

                if (!results.isEmpty()) {
                    matchCountInWindow.add(results.size());
                    lastTradePrice = results.get(results.size() - 1).price();
                    onMatch.accept(results);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Matching thread stopped");
    }

    private void refreshTpsWindow() {
        long now = System.currentTimeMillis();
        long elapsed = now - windowStartMs;
        if (elapsed >= 1_000) {
            lastTps = matchCountInWindow.sumThenReset() * 1_000.0 / elapsed;
            windowStartMs = now;
        }
    }
}
