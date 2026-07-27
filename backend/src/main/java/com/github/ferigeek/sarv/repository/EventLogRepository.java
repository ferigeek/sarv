package com.github.ferigeek.sarv.repository;

import com.github.ferigeek.sarv.entity.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {
}
