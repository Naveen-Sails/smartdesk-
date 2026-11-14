package com.smartdesk.notification_service.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailSenderImpl implements EmailSender {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username:noreply@smartdesk.com}")
    private String fromEmail;

    @Value("${spring.mail.password:mock_password}")
    private String mailPassword;

    @Override
    public void send(String to, String subject, String body) {
        log.info("Processing email dispatch for '{}' with subject '{}'", to, subject);

        // Dev mode fallback: If SMTP credentials are mock or empty, simulate delivery and log OTP to console
        if ("mock_password".equals(mailPassword) || mailPassword == null || mailPassword.isBlank()) {
            log.info("==================================================================");
            log.info(">> [NOTIFICATION ENGINE - DEV SIMULATION]");
            log.info(">> To: {}", to);
            log.info(">> Subject: {}", subject);
            log.info(">> Body: {}", body);
            log.info("==================================================================");
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        javaMailSender.send(message);
        log.info("Email successfully sent to '{}'", to);
    }
}
