package com.sendnforget.worker.controller;

import com.sendnforget.worker.model.JobLog;
import com.sendnforget.worker.repository.JobLogRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class JobStatusController {
    private final JobLogRepository jobLogRepository;

    public JobStatusController(JobLogRepository jobLogRepository) {
        this.jobLogRepository = jobLogRepository;
    }

    @CrossOrigin
    @GetMapping("/jobs")
    public List<JobLog> getJobs() {
        return jobLogRepository.findAll();
    }
}
