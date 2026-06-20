package com.miniexchange.repository;

import com.miniexchange.domain.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {
    List<EventLog> findTop100ByOrderByTimestampDesc();

    /** 리플레이용: 발생 순서(시간→id)대로 전체 이벤트를 조회한다. */
    List<EventLog> findAllByOrderByTimestampAscIdAsc();
}
