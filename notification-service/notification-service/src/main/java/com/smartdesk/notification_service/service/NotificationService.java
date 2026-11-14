package com.smartdesk.notification_service.service;

import com.smartdesk.notification_service.dto.OtpEmailRequest;
import com.smartdesk.notification_service.dto.TicketNotificationRequest;

public interface NotificationService {
    void sendOtpEmailAsync(OtpEmailRequest request);
    void sendTicketNotificationAsync(TicketNotificationRequest request);
}
