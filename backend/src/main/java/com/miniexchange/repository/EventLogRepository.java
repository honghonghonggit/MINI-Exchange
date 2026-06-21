package com.miniexchange.repository;

import com.miniexchange.domain.EventLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {
    List<EventLog> findTop100ByOrderByTimestampDesc();

    /**
     * 리플레이용: 최근 이벤트를 id 내림차순으로 최대 limit건 조회한다(서비스에서 오름차순으로 뒤집어 재생).
     * 설계 결정: event_logs는 무한히 증가하므로 전체 로드는 OOM 위험 → 최근 윈도우만 메모리에 올린다.
     * id는 단일 persist 스레드의 적재 순서라 시간순과 일치한다.
     */
    @Query("SELECT e FROM EventLog e ORDER BY e.id DESC")
    List<EventLog> findRecent(Pageable pageable);

    /** 보존정책용: 현재 최대 id (없으면 null). */
    @Query("SELECT MAX(e.id) FROM EventLog e")
    Long maxId();

    /** 보존정책용: 임계 id 미만(오래된) 이벤트 삭제. */
    @Modifying
    @Query("DELETE FROM EventLog e WHERE e.id < :minId")
    int deleteByIdLessThan(@Param("minId") long minId);
}
