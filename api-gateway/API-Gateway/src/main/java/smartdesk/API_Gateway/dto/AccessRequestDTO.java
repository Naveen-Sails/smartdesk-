package smartdesk.API_Gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import smartdesk.API_Gateway.entity.ApprovalStatus;
import smartdesk.API_Gateway.entity.Role;
import smartdesk.API_Gateway.entity.RoleApprovalRequest;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessRequestDTO {
    private UUID id;
    private UUID userId;
    private String userName;
    private String userEmail;
    private Role currentRole;
    private Role requestedRole;
    private String department;
    private ApprovalStatus status;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private LocalDateTime createdAt;

    public static AccessRequestDTO fromEntity(RoleApprovalRequest req) {
        if (req == null) return null;
        return AccessRequestDTO.builder()
                .id(req.getId())
                .userId(req.getUser() != null ? req.getUser().getId() : null)
                .userName(req.getUser() != null ? req.getUser().getFullName() : "Unknown")
                .userEmail(req.getUser() != null ? req.getUser().getEmail() : "")
                .currentRole(req.getUser() != null ? req.getUser().getRole() : Role.ROLE_EMPLOYEE)
                .requestedRole(req.getRequestedRole())
                .department(req.getDepartment())
                .status(req.getStatus())
                .reviewedByName(req.getReviewedBy() != null ? req.getReviewedBy().getFullName() : null)
                .reviewedAt(req.getReviewedAt())
                .rejectionReason(req.getRejectionReason())
                .createdAt(req.getCreatedAt())
                .build();
    }
}
