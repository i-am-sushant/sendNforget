package com.sendnforget.ingestion.controller;

import com.sendnforget.ingestion.config.RabbitConfig;
import com.sendnforget.ingestion.model.NotificationRequest;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(
    origins = {"http://localhost:5500", "http://127.0.0.1:5500"},
    allowedHeaders = "*",
    methods = {org.springframework.web.bind.annotation.RequestMethod.POST, org.springframework.web.bind.annotation.RequestMethod.OPTIONS}
)
public class IngestionController {
    private final RabbitTemplate rabbitTemplate;

    public IngestionController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/notify")
    public ResponseEntity<Map<String, String>> notify(@RequestBody NotificationRequest request) {
        String trackingId = UUID.randomUUID().toString();

        Map<String, Object> payload = new HashMap<>();
        payload.put("trackingId", trackingId);
        payload.put("clientId", request.clientId());
        payload.put("recipient", request.recipient());
        payload.put("message", request.message());

        rabbitTemplate.convertAndSend(RabbitConfig.TASKS_QUEUE, payload);

        Map<String, String> response = new HashMap<>();
        response.put("trackingId", trackingId);
        response.put("status", "QUEUED");
        return ResponseEntity.accepted().body(response);
    }
}
