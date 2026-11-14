package com.smartdesk.notification_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdesk.notification_service.dto.OtpEmailRequest;
import com.smartdesk.notification_service.exception.GlobalExceptionHandler;
import com.smartdesk.notification_service.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testSendOtpNotification_Success() throws Exception {
        OtpEmailRequest request = OtpEmailRequest.builder()
                .recipientEmail("user@example.com")
                .otp("123456")
                .build();

        doNothing().when(notificationService).sendOtpEmailAsync(any(OtpEmailRequest.class));

        mockMvc.perform(post("/api/v1/notifications/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.message").value("OTP notification request accepted for delivery"));
    }

    @Test
    void testSendOtpNotification_ValidationFailure_InvalidEmail() throws Exception {
        OtpEmailRequest request = OtpEmailRequest.builder()
                .recipientEmail("invalid-email")
                .otp("123456")
                .build();

        mockMvc.perform(post("/api/v1/notifications/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void testSendOtpNotification_ValidationFailure_BlankOtp() throws Exception {
        OtpEmailRequest request = OtpEmailRequest.builder()
                .recipientEmail("user@example.com")
                .otp("")
                .build();

        mockMvc.perform(post("/api/v1/notifications/otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
