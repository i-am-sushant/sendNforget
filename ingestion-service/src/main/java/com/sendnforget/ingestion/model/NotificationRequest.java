package com.sendnforget.ingestion.model;

public record NotificationRequest(String clientId, String recipient, String message) {
}
