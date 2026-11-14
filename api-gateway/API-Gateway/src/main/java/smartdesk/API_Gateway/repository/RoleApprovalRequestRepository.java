package smartdesk.API_Gateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import smartdesk.API_Gateway.entity.ApprovalStatus;
import smartdesk.API_Gateway.entity.Role;
import smartdesk.API_Gateway.entity.RoleApprovalRequest;
import smartdesk.API_Gateway.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleApprovalRequestRepository extends JpaRepository<RoleApprovalRequest, UUID> {
    List<RoleApprovalRequest> findByStatusOrderByCreatedAtDesc(ApprovalStatus status);

    List<RoleApprovalRequest> findByRequestedRoleAndStatusOrderByCreatedAtDesc(Role requestedRole, ApprovalStatus status);

    List<RoleApprovalRequest> findByRequestedRoleAndDepartmentIgnoreCaseAndStatusOrderByCreatedAtDesc(Role requestedRole, String department, ApprovalStatus status);

    Optional<RoleApprovalRequest> findTopByUserAndStatusOrderByCreatedAtDesc(User user, ApprovalStatus status);

    List<RoleApprovalRequest> findByUserOrderByCreatedAtDesc(User user);
}
