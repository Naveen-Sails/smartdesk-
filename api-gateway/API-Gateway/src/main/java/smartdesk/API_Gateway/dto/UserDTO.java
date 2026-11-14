package smartdesk.API_Gateway.dto;

import lombok.*;
import smartdesk.API_Gateway.entity.Role;
import smartdesk.API_Gateway.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {
    private UUID id;
    private String fullName;
    private String email;
    private Role role;
    private String department;
    private Boolean isRootAdmin;
    private Boolean isActive;
    private LocalDateTime createdAt;

    public static UserDTO fromEntity(User user) {
        if (user == null) {
            return null;
        }
        return UserDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole())
                .department(user.getDepartment())
                .isRootAdmin(user.getIsRootAdmin())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
