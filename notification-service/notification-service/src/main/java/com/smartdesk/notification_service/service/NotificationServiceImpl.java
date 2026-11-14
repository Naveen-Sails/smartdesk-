package com.smartdesk.notification_service.service;

import com.smartdesk.notification_service.dto.OtpEmailRequest;
import com.smartdesk.notification_service.entity.NotificationLog;
import com.smartdesk.notification_service.entity.NotificationStatus;
import com.smartdesk.notification_service.repository.NotificationLogRepository;
import com.smartdesk.notification_service.strategy.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final EmailSender emailSender;

    @Override
    @Async
    @Transactional
    public void sendOtpEmailAsync(OtpEmailRequest request) {
        String recipient = request.getRecipientEmail();
        String subject = "SmartDesk Login Verification Code";
        String body = "Your SmartDesk login code is: " + request.getOtp() + ". It expires in 5 minutes.";

        log.info("Initiating async OTP email delivery for recipient: {}", recipient);

        NotificationLog logEntry = NotificationLog.builder()
                .recipientEmail(recipient)
                .notificationType("EMAIL_OTP")
                .subject(subject)
                .status(NotificationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        logEntry = notificationLogRepository.save(logEntry);

        try {
            emailSender.send(recipient, subject, body);
            logEntry.setStatus(NotificationStatus.SUCCESS);
            logEntry.setSentAt(LocalDateTime.now());
            log.info("Successfully delivered OTP email to recipient: {}", recipient);
        } catch (Exception ex) {
            log.error("Failed to deliver OTP email to recipient: {}. Error: {}", recipient, ex.getMessage(), ex);
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage(ex.getMessage());
        }

        notificationLogRepository.save(logEntry);
    }

    @Override
    @Async
    @Transactional
    public void sendTicketNotificationAsync(com.smartdesk.notification_service.dto.TicketNotificationRequest request) {
        String recipient = request.getRecipientEmail();
        String eventType = request.getEventType() != null ? request.getEventType() : "UPDATE";
        java.util.Map<String, String> params = request.getParams() != null ? request.getParams() : java.util.Map.of();
        String userName = params.getOrDefault("name", "User");
        String ticketCode = params.getOrDefault("code", "SD-" + request.getTicketId());
        String ticketTitle = params.getOrDefault("title", "Support Request");
        String status = params.getOrDefault("status", eventType);
        String actor = params.getOrDefault("actor", "Support Agent");

        String subject;
        String body;

        if ("TICKET_CREATED".equalsIgnoreCase(eventType)) {
            subject = String.format("[SmartDesk] Ticket %s Created: %s", ticketCode, ticketTitle);
            body = String.format(
                    "Hello %s,\n\nYour support ticket %s (\"%s\") has been successfully created and is currently OPEN.\nPriority: %s | Department: %s\n\nWe will notify you by email as soon as an agent begins working on it.\n\nBest regards,\nSmartDesk Support Team",
                    userName, ticketCode, ticketTitle, params.getOrDefault("priority", "MEDIUM"), params.getOrDefault("category", "IT"));
        } else if ("STATUS_CHANGED".equalsIgnoreCase(eventType)) {
            subject = String.format("[SmartDesk] Ticket %s Status Updated: %s", ticketCode, status);
            body = String.format(
                    "Hello %s,\n\nYour support ticket %s (\"%s\") status has changed.\n\nNew Status: %s\nUpdated by: %s\n\nYou can view and track your ticket at http://localhost:5173.\n\nBest regards,\nSmartDesk Support Team",
                    userName, ticketCode, ticketTitle, status, actor);
        } else if ("TICKET_ASSIGNED".equalsIgnoreCase(eventType)) {
            subject = String.format("[SmartDesk] Ticket %s Assigned: %s", ticketCode, ticketTitle);
            body = String.format(
                    "Hello %s,\n\nTicket %s (\"%s\") has been assigned to you.\nPriority: %s\n\nPlease review your queue to begin working on it.\n\nBest regards,\nSmartDesk Support Team",
                    userName, ticketCode, ticketTitle, params.getOrDefault("priority", "MEDIUM"));
        } else {
            subject = String.format("[SmartDesk] Ticket %s Update: %s", ticketCode, eventType);
            body = String.format(
                    "Hello %s,\n\nYour ticket %s has a new update: %s.\n\nBest regards,\nSmartDesk Support Team",
                    userName, ticketCode, eventType);
        }

        log.info("Initiating async ticket notification: type={}, ticketId={}, recipient={}, subject='{}'",
                eventType, request.getTicketId(), recipient, subject);

        NotificationLog logEntry = NotificationLog.builder()
                .recipientEmail(recipient != null ? recipient : "no-email-provided")
                .notificationType("TICKET_" + eventType)
                .subject(subject)
                .status(NotificationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        logEntry = notificationLogRepository.save(logEntry);

        if (recipient != null && !recipient.isBlank()) {
            try {
                emailSender.send(recipient, subject, body);
                logEntry.setStatus(NotificationStatus.SUCCESS);
                logEntry.setSentAt(LocalDateTime.now());
                log.info("Delivered ticket event email to: {}", recipient);
            } catch (Exception ex) {
                log.warn("Failed to deliver ticket email to: {}. Error: {}", recipient, ex.getMessage());
                logEntry.setStatus(NotificationStatus.FAILED);
                logEntry.setErrorMessage(ex.getMessage());
            }
        } else {
            logEntry.setStatus(NotificationStatus.SUCCESS);
            logEntry.setErrorMessage("Logged without outbound email (recipient email empty)");
        }

        notificationLogRepository.save(logEntry);
    }
}
