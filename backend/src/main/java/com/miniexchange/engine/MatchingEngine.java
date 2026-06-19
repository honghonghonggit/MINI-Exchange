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
 *   - DB 저장은 onMatch 콜백에서 비동기 처리 (매칭 스레드를 블로킹하지 않음)
 *   - lastSnapshot: volatile 참조 + 불변 record → REST 스레드가 안전하게 읽음
 */
@Slf4j
public class MatchingEngine {

    private final LinkedBlockingQueue<OrderCommand> commandQueue = new LinkedBlockingQueue<>();
    private final OrderBook orderBook = new OrderBook();
    private final Consumer<List<MatchResult>> onMatch;
    private final Thread matchingThread;

    private volatile OrderBookSnapshot lastSnapshot = new OrderBookSnapshot(List.of(), List.of());

    public MatchingEngine(Consumer<List<MatchResult>> onMatch) {
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

    /** REST 스레드에서 호출 가능 — volatile 참조라 항상 일관된 스냅샷을 반환 */
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
                switch (cmd.type()) {
                    case SUBMIT -> {
                        List<MatchResult> results = orderBook.submit(cmd.order());
                        if (!results.isEmpty()) {
                            onMatch.accept(results);
                        }
                    }
                    case CANCEL -> orderBook.cancel(cmd.cancelOrderId());
                }
                lastSnapshot = orderBook.snapshot(); // 매 명령 처리 후 스냅샷 갱신
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        log.info("Matching thread stopped");
    }
}
