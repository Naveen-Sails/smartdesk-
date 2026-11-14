package com.smartdesk.notification_service.service;

import com.smartdesk.notification_service.dto.OtpEmailRequest;
import com.smartdesk.notification_service.entity.NotificationLog;
import com.smartdesk.notification_service.entity.NotificationStatus;
import com.smartdesk.notification_service.repository.NotificationLogRepository;
import com.smartdesk.notification_service.strategy.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> {
            NotificationLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            return log;
        });
    }

    @Test
    void testSendOtpEmailAsync_Success() {
        OtpEmailRequest request = OtpEmailRequest.builder()
                .recipientEmail("employee@smartdesk.com")
                .otp("654321")
                .build();

        doNothing().when(emailSender).send(eq("employee@smartdesk.com"), anyString(), contains("654321"));

        notificationService.sendOtpEmailAsync(request);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, atLeast(2)).save(captor.capture());

        NotificationLog finalLog = captor.getValue();
        assertEquals(NotificationStatus.SUCCESS, finalLog.getStatus());
        assertNotNull(finalLog.getSentAt());
        assertNull(finalLog.getErrorMessage());
        verify(emailSender, times(1)).send(eq("employee@smartdesk.com"), eq("SmartDesk Login Verification Code"), contains("654321"));
    }

    @Test
    void testSendOtpEmailAsync_EmailFailure() {
        OtpEmailRequest request = OtpEmailRequest.builder()
                .recipientEmail("employee@smartdesk.com")
                .otp("654321")
                .build();

        doThrow(new RuntimeException("SMTP host connection refused"))
                .when(emailSender).send(anyString(), anyString(), anyString());

        notificationService.sendOtpEmailAsync(request);

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, atLeast(2)).save(captor.capture());

        NotificationLog finalLog = captor.getValue();
        assertEquals(NotificationStatus.FAILED, finalLog.getStatus());
        assertTrue(finalLog.getErrorMessage().contains("SMTP host connection refused"));
    }
}
