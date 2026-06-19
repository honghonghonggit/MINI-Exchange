package com.miniexchange.config;

import com.miniexchange.engine.MatchingEngine;
import com.miniexchange.service.PersistService;
import com.miniexchange.ws.OrderBookPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 설계 결정:
 *   - MatchingEngine 싱글턴 빈. 앱 생명주기와 함께 시작.
 *   - onOrderBookChanged: 오더북 변경 시마다 호출 → WS 브로드캐스트 (주문 추가/취소 포함)
 *   - onMatch: 체결 발생 시만 호출 → 비동기 DB persist
 *   - 두 콜백 분리로 circular dependency 없음 (Publisher는 Engine에 의존하지 않음)
 *   - persistExecutor: core 1개로 Execution 저장 순서 보장, 큐 10000으로 버스트 흡수
 */
@Configuration
@EnableAsync
public class MatchingEngineConfig {

    @Bean
    public MatchingEngine matchingEngine(OrderBookPublisher publisher, PersistService persistService) {
        return new MatchingEngine(
                snapshot -> publisher.broadcast(snapshot),  // 항상: WS 브로드캐스트
                results  -> persistService.save(results)    // 체결 시: 비동기 DB persist
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
