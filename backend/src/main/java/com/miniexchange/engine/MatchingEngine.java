package com.miniexchange.engine;

import com.miniexchange.domain.Order;
import com.miniexchange.domain.OrderType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
 *   - onViStateChanged: VI 정지/해제 전환 시 호출 → WS /topic/vi + 이벤트 로그
 *   - lastSnapshot: volatile + 불변 record → REST 스레드가 안전하게 읽음
 *   - 메트릭: AtomicLong으로 레이턴시·TPS 추적 (lock-free)
 *   - VI(VolatilityGuard): 매칭 스레드 안에서만 상태를 다뤄 lock 없이 정지/일괄체결 처리
 */
@Slf4j
public class MatchingEngine {

    private final LinkedBlockingQueue<OrderCommand> commandQueue = new LinkedBlockingQueue<>();
    private final OrderBook orderBook = new OrderBook();
    private final Consumer<OrderBookSnapshot> onOrderBookChanged;
    private final Consumer<List<MatchResult>> onMatch;
    private final Consumer<ViState> onViStateChanged;
    private final VolatilityGuard guard;
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

    /** VI 비활성 생성자 (기존 호출부·테스트 호환). */
    public MatchingEngine(Consumer<OrderBookSnapshot> onOrderBookChanged,
                          Consumer<List<MatchResult>> onMatch) {
        this(onOrderBookChanged, onMatch, viState -> {}, VolatilityGuard.disabled());
    }

    public MatchingEngine(Consumer<OrderBookSnapshot> onOrderBookChanged,
                          Consumer<List<MatchResult>> onMatch,
                          Consumer<ViState> onViStateChanged,
                          VolatilityGuard guard) {
        this.onOrderBookChanged = onOrderBookChanged;
        this.onMatch = onMatch;
        this.onViStateChanged = onViStateChanged;
        this.guard = guard;
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
                // poll(timeout): 정지 중 신규 명령이 없어도 주기적으로 깨어나 해제 시점을 체크
                OrderCommand cmd = commandQueue.poll(200, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();

                // VI 해제: cooldown 경과 시 쌓인 주문을 일괄 체결(uncross)
                if (guard.halted() && now >= guard.haltUntil()) {
                    List<MatchResult> uncross = orderBook.uncross();
                    guard.release(lastTradePrice, now);
                    log.info("VI 해제: 일괄체결 {}건, 기준가 {}", uncross.size(), guard.referencePrice());
                    publishMatches(uncross);
                    onViStateChanged.accept(new ViState(false, guard.referencePrice(), 0L));
                }

                if (cmd == null) continue; // 타임아웃 → 해제 체크만 하고 다음 루프

                long start = System.nanoTime();
                List<MatchResult> results = List.of();
                switch (cmd.type()) {
                    case SUBMIT -> results = handleSubmit(cmd.order(), now);
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
                    // 방금 트리거됐다면 기준가를 갱신하지 않는다(밴드가 재중심되어 정지가 무의미해짐 방지)
                    if (!guard.halted()) guard.maybeUpdateReference(lastTradePrice, now);
                    onMatch.accept(results);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Matching thread stopped");
    }

    /**
     * SUBMIT 처리. VI 상태에 따라 분기 (정적 VI):
     *   - 정지 중: 매칭 없이 안착(LIMIT만, MARKET은 거부) → 해제 시 일괄 체결됨
     *   - 정상: 매칭 실행 후, 체결가(가장 깊은 체결)가 기준가 밴드를 벗어나면 VI 발동.
     *           즉 "밴드 밖 가격이 프린트되면 그 직후부터 정지"하는 한국식 정적 VI에 가깝다.
     */
    private List<MatchResult> handleSubmit(Order order, long now) {
        if (guard.halted()) {
            if (order.getType() == OrderType.LIMIT) orderBook.rest(order);
            return List.of();
        }
        List<MatchResult> results = orderBook.submit(order);
        if (!results.isEmpty()) {
            long execPrice = results.get(results.size() - 1).price(); // 가장 깊은 체결가
            if (guard.wouldTrigger(execPrice)) {
                guard.trigger(now);
                log.info("VI 발동: 체결가 {} (기준가 {}) → {}ms 정지",
                        execPrice, guard.referencePrice(), guard.haltUntil() - now);
                onViStateChanged.accept(new ViState(true, guard.referencePrice(), guard.haltUntil()));
            }
        }
        return results;
    }

    /** uncross 등 큐 외부에서 발생한 체결을 스냅샷·체결 콜백으로 발행한다. */
    private void publishMatches(List<MatchResult> results) {
        lastSnapshot = orderBook.snapshot();
        onOrderBookChanged.accept(lastSnapshot);
        if (!results.isEmpty()) {
            matchCountInWindow.add(results.size());
            lastTradePrice = results.get(results.size() - 1).price();
            onMatch.accept(results);
        }
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
