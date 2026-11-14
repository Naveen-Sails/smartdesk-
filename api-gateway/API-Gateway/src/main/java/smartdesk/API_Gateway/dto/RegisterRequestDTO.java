package smartdesk.API_Gateway.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import smartdesk.API_Gateway.entity.Role;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequestDTO {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    private String email;

    @Builder.Default
    private Role role = Role.ROLE_EMPLOYEE;

    private String department;

    @Builder.Default
    private Boolean isRootAdmin = false;
}
