package com.miniexchange.service;

import com.miniexchange.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * event_logs 보존정책.
 * 설계 결정: 시뮬레이터가 24시간 이벤트를 적재해 event_logs가 무한히 증가한다. 상시 배포
 *   (제한된 메모리·DB 용량) 환경에서 (1) 리플레이 전체 로드로 인한 OOM, (2) DB 무한 증가를 막기 위해
 *   최근 MAX_EVENTS건만 남기고 주기적으로 오래된 이벤트를 삭제한다. 입력(주문/체결) 흐름 자체에는
 *   영향을 주지 않으며, 리플레이가 보는 윈도우도 자연히 유한해진다.
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class EventLogRetention {

    private static final long MAX_EVENTS = 50_000L; // 유지할 최근 이벤트 수

    private final EventLogRepository eventLogRepository;

    @Scheduled(fixedDelay = 60_000L)
    @Transactional
    public void prune() {
        Long maxId = eventLogRepository.maxId();
        if (maxId == null) return;
        long minKeep = maxId - MAX_EVENTS;
        if (minKeep <= 0) return;
        int deleted = eventLogRepository.deleteByIdLessThan(minKeep);
        if (deleted > 0) {
            log.info("event_logs 보존정리: {}건 삭제 (id < {}, 최근 {}건 유지)", deleted, minKeep, MAX_EVENTS);
        }
    }
}
