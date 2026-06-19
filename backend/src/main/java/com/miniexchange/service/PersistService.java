package com.miniexchange.service;

import com.miniexchange.domain.Execution;
import com.miniexchange.engine.MatchResult;
import com.miniexchange.repository.ExecutionRepository;
import com.miniexchange.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 매칭 스레드가 생성한 체결 결과를 DB에 비동기로 저장한다.
 * 설계 결정:
 *   - @Async("persistExecutor"): 매칭 스레드를 블로킹하지 않음
 *   - 같은 트랜잭션 내에서 Execution 저장 + Order 상태 업데이트 → 일관성 유지
 *   - 서버 비정상 종료 시 미저장 체결이 유실될 수 있음 (시뮬레이터 허용 트레이드오프)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersistService {

    private final OrderRepository orderRepository;
    private final ExecutionRepository executionRepository;

    @Async("persistExecutor")
    @Transactional
    public void save(List<MatchResult> results) {
        for (MatchResult r : results) {
            executionRepository.save(Execution.builder()
                    .buyOrder(r.buyOrder())
                    .sellOrder(r.sellOrder())
                    .price(r.price())
                    .quantity(r.quantity())
                    .executedAt(LocalDateTime.now())
                    .build());

            // Order 상태(remainingQuantity, status)를 DB에 반영
            orderRepository.save(r.makerOrder());
            orderRepository.save(r.takerOrder());

            log.debug("Persisted execution: price={} qty={}", r.price(), r.quantity());
        }
    }
}
