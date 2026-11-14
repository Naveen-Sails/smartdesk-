package com.smartdesk.notification_service.dto;

import lombok.*;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketNotificationRequest {
    private UUID eventId;
    private String eventType;
    private UUID recipientUserId;
    private String recipientEmail;
    private Long ticketId;
    private Map<String, String> params;
}
