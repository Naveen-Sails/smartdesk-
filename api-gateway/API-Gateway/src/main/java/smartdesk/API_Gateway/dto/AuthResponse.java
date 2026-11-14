package smartdesk.API_Gateway.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private UserDTO user;

    public AuthResponse(String accessToken, UserDTO user) {
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
        this.user = user;
    }
}
