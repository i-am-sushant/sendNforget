package com.sendnforget.worker.model;

public record NotificationRequest(String trackingId, String clientId, String recipient, String message) {
}
