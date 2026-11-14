package com.smartdesk.notification_service.controller;

import com.smartdesk.notification_service.dto.NotificationResponse;
import com.smartdesk.notification_service.dto.OtpEmailRequest;
import com.smartdesk.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/otp")
    public ResponseEntity<NotificationResponse> sendOtpNotification(@Valid @RequestBody OtpEmailRequest request) {
        log.info("Received OTP notification request for: {}", request.getRecipientEmail());
        notificationService.sendOtpEmailAsync(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(NotificationResponse.accepted("OTP notification request accepted for delivery"));
    }

    @PostMapping("/send")
    public ResponseEntity<NotificationResponse> sendTicketNotification(@RequestBody com.smartdesk.notification_service.dto.TicketNotificationRequest request) {
        log.info("Received ticket event notification: type={}, ticketId={}, recipient={}",
                request.getEventType(), request.getTicketId(), request.getRecipientEmail());
        notificationService.sendTicketNotificationAsync(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(NotificationResponse.accepted("Ticket notification request accepted for delivery"));
    }
}
