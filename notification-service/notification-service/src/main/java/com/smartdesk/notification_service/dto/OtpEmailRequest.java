package com.smartdesk.notification_service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpEmailRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Recipient email must be valid")
    @JsonAlias({"email", "recipientEmail"})
    private String recipientEmail;

    @NotBlank(message = "OTP is required")
    private String otp;
}
