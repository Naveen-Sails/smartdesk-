package com.smartdesk.notification_service.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private String status;
    private String message;
    private LocalDateTime timestamp;

    public static NotificationResponse accepted(String message) {
        return NotificationResponse.builder()
                .status("ACCEPTED")
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
