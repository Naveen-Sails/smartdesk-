package com.smartdesk.notification_service.strategy;

public interface EmailSender {
    void send(String to, String subject, String body);
}
