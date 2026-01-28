package com.sendnforget.worker.consumer;

import com.sendnforget.worker.config.RabbitConfig;
import com.sendnforget.worker.model.JobLog;
import com.sendnforget.worker.model.NotificationRequest;
import com.sendnforget.worker.repository.JobLogRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Random;

@Component
public class WorkerConsumer {
    private final JobLogRepository jobLogRepository;
    private final JavaMailSender mailSender;
    private final String mailFrom;
    private final Random random = new Random();
    private static final Logger logger = LoggerFactory.getLogger(WorkerConsumer.class);

    public WorkerConsumer(
            JobLogRepository jobLogRepository,
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String mailFrom
    ) {
        this.jobLogRepository = jobLogRepository;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
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

        try {
            sendNotificationEmail(request);
        } catch (Exception ex) {
            log.setStatus("FAILED");
            jobLogRepository.save(log);
            logger.error("Failed to send email for trackingId={}", id, ex);
            throw new RuntimeException("Email delivery failed", ex);
        }

        log.setStatus("SENT");
        jobLogRepository.save(log);
    }

    private void sendNotificationEmail(NotificationRequest request) {
        if (mailFrom == null || mailFrom.isBlank()) {
            throw new IllegalStateException("GMAIL_SMTP_USER is not configured");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(request.recipient());
        message.setSubject("[sendNforget][" + request.clientId() + "] Notification");
        message.setText(request.message());
        mailSender.send(message);
    }
}
