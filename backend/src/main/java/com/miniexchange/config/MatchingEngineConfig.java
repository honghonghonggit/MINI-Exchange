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
 *   - MatchingEngine은 싱글턴 빈. 애플리케이션 생명주기와 함께 시작/종료.
 *   - onMatch 콜백: WebSocket 브로드캐스트(동기) → DB persist(비동기).
 *     매칭 스레드가 DB I/O로 블로킹되지 않도록 persist는 별도 executor에서 처리.
 *   - persistExecutor: 코어 1개로 순서 보장, 큐 10000으로 버스트 흡수.
 */
@Configuration
@EnableAsync
public class MatchingEngineConfig {

    @Bean
    public MatchingEngine matchingEngine(PersistService persistService, OrderBookPublisher publisher) {
        return new MatchingEngine(results -> {
            publisher.broadcast(results);   // WebSocket (Phase 1: 스텁)
            persistService.save(results);   // 비동기 DB persist
        });
    }

    @Bean("persistExecutor")
    public ThreadPoolTaskExecutor persistExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);   // 순서 보장
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10_000);
        executor.setThreadNamePrefix("persist-");
        executor.initialize();
        return executor;
    }
}
