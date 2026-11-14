package smartdesk.API_Gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import smartdesk.API_Gateway.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffMemberDTO {
    private UUID id;
    private String name;
    private String email;
    private String role;
    private String department;
    private LocalDateTime createdAt;

    public static StaffMemberDTO fromEntity(User user) {
        if (user == null) return null;
        return StaffMemberDTO.builder()
                .id(user.getId())
                .name(user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getEmail())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : "ROLE_AGENT")
                .department(user.getDepartment() != null ? user.getDepartment() : "IT")
                .createdAt(user.getCreatedAt())
                .build();
    }
}
