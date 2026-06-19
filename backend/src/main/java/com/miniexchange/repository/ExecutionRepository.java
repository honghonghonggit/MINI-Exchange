package com.miniexchange.repository;

import com.miniexchange.domain.Execution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {
    List<Execution> findTop50ByOrderByExecutedAtDesc();
}
