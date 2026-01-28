package com.sendnforget.worker.repository;

import com.sendnforget.worker.model.JobLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobLogRepository extends JpaRepository<JobLog, String> {
}
