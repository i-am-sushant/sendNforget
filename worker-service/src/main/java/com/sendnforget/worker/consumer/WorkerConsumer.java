package com.sendnforget.worker.consumer;

import com.sendnforget.worker.config.RabbitConfig;
import com.sendnforget.worker.model.JobLog;
import com.sendnforget.worker.model.NotificationRequest;
import com.sendnforget.worker.repository.JobLogRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
//import java.util.Optional;
import java.util.Random;

@Component
public class WorkerConsumer {
    private final JobLogRepository jobLogRepository;
    private final Random random = new Random();

    public WorkerConsumer(JobLogRepository jobLogRepository) {
        this.jobLogRepository = jobLogRepository;
    }

    @RabbitListener(queues = RabbitConfig.TASKS_QUEUE)
    public void handle(NotificationRequest request) throws InterruptedException {
        String id = request.trackingId();

        JobLog log = jobLogRepository.findById(id)
                .orElseGet(() -> new JobLog(id, "PROCESSING", request.recipient(), 0, Instant.now()));

        log.setStatus("PROCESSING");
        log.setRecipient(request.recipient());
        log.setRetryCount(log.getRetryCount() + 1);
        jobLogRepository.save(log);

        Thread.sleep(2000);

        if (random.nextDouble() < 0.3) {
            log.setStatus("FAILED");
            jobLogRepository.save(log);
            throw new RuntimeException("Simulated transient failure");
        }

        log.setStatus("SENT");
        jobLogRepository.save(log);
    }
}
