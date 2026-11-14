package smartdesk.API_Gateway.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse {
    private String message;
    private int status;
    private LocalDateTime timestamp;

    public static ApiResponse ok(String message) {
        return ApiResponse.builder()
                .message(message)
                .status(200)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
