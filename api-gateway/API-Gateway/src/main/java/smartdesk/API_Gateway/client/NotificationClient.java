package smartdesk.API_Gateway.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import smartdesk.API_Gateway.dto.NotificationOtpPayload;

@Component
@Slf4j
public class NotificationClient {

    private final WebClient webClient;
    private final String notificationServiceUrl;

    public NotificationClient(WebClient.Builder webClientBuilder,
                              @Value("${notification.service.url:http://localhost:8083}") String notificationServiceUrl) {
        this.webClient = webClientBuilder.build();
        this.notificationServiceUrl = notificationServiceUrl;
    }

    public void sendOtpNotificationAsync(String email, String otp) {
        String endpoint = notificationServiceUrl + "/api/v1/notifications/otp";
        log.info("Dispatching async OTP notification to {} via {}", email, endpoint);

        NotificationOtpPayload payload = NotificationOtpPayload.builder()
                .recipientEmail(email)
                .otp(otp)
                .build();

        webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(response -> log.info("Async notification successfully sent for email: {}", email))
                .doOnError(error -> log.error("Error sending notification for email {}: {}", email, error.getMessage()))
                .subscribe();
    }
}
