package com.miniexchange.config;

import com.miniexchange.engine.MatchingEngine;
import com.miniexchange.engine.VolatilityGuard;
import com.miniexchange.service.EventLogService;
import com.miniexchange.service.PersistService;
import com.miniexchange.ws.OrderBookPublisher;
import com.miniexchange.ws.TradePublisher;
import com.miniexchange.ws.ViPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 설계 결정:
 *   - MatchingEngine 싱글턴 빈. 앱 생명주기와 함께 시작.
 *   - onOrderBookChanged: 오더북 변경 시마다 호출 → WS 브로드캐스트
 *   - onMatch: 체결 발생 시만 호출 → WS 체결 이벤트 + 비동기 DB persist + 이벤트 로그
 *   - 두 콜백 분리로 circular dependency 없음 (Publisher는 Engine에 의존하지 않음)
 *   - persistExecutor: core 1개로 Execution/EventLog 저장 순서 보장, 큐 10000으로 버스트 흡수
 */
@Configuration
@EnableAsync
public class MatchingEngineConfig {

    @Bean
    public MatchingEngine matchingEngine(OrderBookPublisher orderBookPublisher,
                                         TradePublisher tradePublisher,
                                         PersistService persistService,
                                         EventLogService eventLogService,
                                         ViPublisher viPublisher) {
        // VI: 기준가 ±2.5% 이탈 체결 시 5초 정지, 기준가는 8초 간격 느린 앵커
        VolatilityGuard guard = new VolatilityGuard(0.025, 5_000, 8_000);
        return new MatchingEngine(
                snapshot -> orderBookPublisher.broadcast(snapshot),
                results -> {
                    tradePublisher.broadcast(results);       // 즉시: WS 체결 이벤트
                    persistService.save(results);            // 비동기: DB persist
                    eventLogService.logExecutions(results);  // 비동기: 이벤트 로그
                },
                viState -> {
                    viPublisher.broadcast(viState);          // 즉시: WS VI 상태
                    eventLogService.logVi(viState);          // 비동기: 이벤트 로그
                },
                guard
        );
    }

    @Bean("persistExecutor")
    public ThreadPoolTaskExecutor persistExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("persist-");
        executor.initialize();
        return executor;
    }
}
