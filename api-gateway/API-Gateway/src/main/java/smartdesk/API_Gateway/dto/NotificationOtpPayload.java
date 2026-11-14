package smartdesk.API_Gateway.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationOtpPayload {
    private String recipientEmail;
    private String otp;
}
