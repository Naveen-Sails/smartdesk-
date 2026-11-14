package smartdesk.API_Gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpRequestDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;
}
