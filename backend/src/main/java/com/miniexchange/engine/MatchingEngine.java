package com.miniexchange.engine;

import com.miniexchange.domain.Order;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * 단일 매칭 스레드 + 커맨드 큐 패턴.
 * 설계 결정:
 *   - 외부 스레드들은 commandQueue에 명령을 넣고 즉시 반환
 *   - matchingThread 혼자 OrderBook을 읽고 씀 → lock 없이 race condition 원천 차단
 *   - onOrderBookChanged: 매 명령 처리 후 항상 호출 → WebSocket 브로드캐스트
 *   - onMatch: 체결이 발생한 경우에만 호출 → 비동기 DB persist
 *   - lastSnapshot: volatile + 불변 record → REST 스레드가 안전하게 읽음
 */
@Slf4j
public class MatchingEngine {

    private final LinkedBlockingQueue<OrderCommand> commandQueue = new LinkedBlockingQueue<>();
    private final OrderBook orderBook = new OrderBook();
    private final Consumer<OrderBookSnapshot> onOrderBookChanged;
    private final Consumer<List<MatchResult>> onMatch;
    private final Thread matchingThread;

    private volatile OrderBookSnapshot lastSnapshot = new OrderBookSnapshot(List.of(), List.of());

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

    /** volatile 참조 → REST 스레드에서 안전하게 읽기 가능 */
    public OrderBookSnapshot snapshot() {
        return lastSnapshot;
    }

    public void stop() {
        matchingThread.interrupt();
    }

    private void loop() {
        log.info("Matching thread started");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                OrderCommand cmd = commandQueue.take();
                List<MatchResult> results = List.of();

                switch (cmd.type()) {
                    case SUBMIT -> results = orderBook.submit(cmd.order());
                    case CANCEL -> orderBook.cancel(cmd.cancelOrderId());
                }

                lastSnapshot = orderBook.snapshot();
                onOrderBookChanged.accept(lastSnapshot);   // 항상: WS 브로드캐스트

                if (!results.isEmpty()) {
                    onMatch.accept(results);               // 체결 시만: DB persist
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Matching thread stopped");
    }
}
