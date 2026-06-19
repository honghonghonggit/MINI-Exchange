package com.miniexchange.repository;

import com.miniexchange.domain.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {
    List<EventLog> findTop100ByOrderByTimestampDesc();
}
