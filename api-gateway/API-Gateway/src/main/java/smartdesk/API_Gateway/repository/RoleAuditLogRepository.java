package smartdesk.API_Gateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import smartdesk.API_Gateway.entity.RoleAuditLog;
import smartdesk.API_Gateway.entity.User;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoleAuditLogRepository extends JpaRepository<RoleAuditLog, UUID> {
    List<RoleAuditLog> findByTargetUserOrderByChangedAtDesc(User targetUser);
}
