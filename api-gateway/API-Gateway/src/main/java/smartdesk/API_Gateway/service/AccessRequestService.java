package smartdesk.API_Gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartdesk.API_Gateway.dto.AccessRequestDTO;
import smartdesk.API_Gateway.entity.ApprovalStatus;
import smartdesk.API_Gateway.entity.Role;
import smartdesk.API_Gateway.entity.RoleApprovalRequest;
import smartdesk.API_Gateway.entity.RoleAuditLog;
import smartdesk.API_Gateway.entity.User;
import smartdesk.API_Gateway.repository.RoleApprovalRequestRepository;
import smartdesk.API_Gateway.repository.RoleAuditLogRepository;
import smartdesk.API_Gateway.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessRequestService {

    private final RoleApprovalRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final RoleAuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public List<AccessRequestDTO> getPendingRequests(UUID callerId, String callerRole, String callerDept) {
        String normalizedRole = (callerRole != null) ? callerRole.toUpperCase().replace("ROLE_", "") : "";
        log.info("Fetching pending access requests for caller ID: {}, role: {}, department: {}", callerId, normalizedRole, callerDept);

        List<RoleApprovalRequest> requests;
        if ("ADMIN".equals(normalizedRole)) {
            // Admins review all pending requests (Managers and Agents)
            requests = requestRepository.findByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING);
        } else if ("MANAGER".equals(normalizedRole)) {
            // Managers review only Agent requests for their specific department
            String dept = (callerDept != null && !callerDept.isBlank()) ? callerDept.trim() : "IT";
            requests = requestRepository.findByRequestedRoleAndDepartmentIgnoreCaseAndStatusOrderByCreatedAtDesc(
                    Role.ROLE_AGENT, dept, ApprovalStatus.PENDING);
        } else {
            requests = Collections.emptyList();
        }

        return requests.stream().map(AccessRequestDTO::fromEntity).toList();
    }

    @Transactional
    public AccessRequestDTO approveRequest(UUID requestId, UUID callerId, String callerRole, String callerDept) {
        String normalizedRole = (callerRole != null) ? callerRole.toUpperCase().replace("ROLE_", "") : "";
        log.info("Approving access request ID: {} by caller: {} (role: {}, dept: {})", requestId, callerId, normalizedRole, callerDept);

        RoleApprovalRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found with ID: " + requestId));

        if (req.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Access request is already " + req.getStatus());
        }

        User caller = (callerId != null) ? userRepository.findById(callerId).orElse(null) : null;

        // Hierarchy Enforcement:
        // 1. Manager requests can ONLY be approved by Admin
        if (req.getRequestedRole() == Role.ROLE_MANAGER) {
            if (!"ADMIN".equals(normalizedRole)) {
                log.warn("Unauthorized approval attempt: only Admin can approve Manager access");
                throw new SecurityException("Only an Admin can approve Manager access requests.");
            }
        }

        // 2. Agent requests can be approved by Admin or Manager of that department
        if (req.getRequestedRole() == Role.ROLE_AGENT) {
            if (!"ADMIN".equals(normalizedRole)) {
                if (!"MANAGER".equals(normalizedRole)) {
                    throw new SecurityException("Only Managers or Admins can approve Agent access requests.");
                }
                String reqDept = (req.getDepartment() != null) ? req.getDepartment().trim() : "";
                String mDept = (callerDept != null) ? callerDept.trim() : "";
                if (!reqDept.equalsIgnoreCase(mDept)) {
                    log.warn("Manager department mismatch: manager is '{}', request is '{}'", mDept, reqDept);
                    throw new SecurityException("Department Managers can only approve Agents for their own department (" + mDept + ").");
                }
            }
        }

        // Apply approval
        req.setStatus(ApprovalStatus.APPROVED);
        req.setReviewedBy(caller);
        req.setReviewedAt(LocalDateTime.now());
        RoleApprovalRequest savedReq = requestRepository.save(req);

        // Update target user
        User targetUser = req.getUser();
        Role previousRole = targetUser.getRole();
        targetUser.setRole(req.getRequestedRole());
        if (req.getDepartment() != null && !req.getDepartment().isBlank()) {
            targetUser.setDepartment(req.getDepartment());
        }
        userRepository.save(targetUser);

        // Audit log
        RoleAuditLog auditLog = RoleAuditLog.builder()
                .targetUser(targetUser)
                .promotedBy(caller)
                .previousRole(previousRole)
                .newRole(targetUser.getRole())
                .changedAt(LocalDateTime.now())
                .build();
        auditLogRepository.save(auditLog);

        log.info("Successfully approved request {}: user '{}' promoted from {} to {}",
                requestId, targetUser.getEmail(), previousRole, targetUser.getRole());

        return AccessRequestDTO.fromEntity(savedReq);
    }

    @Transactional
    public AccessRequestDTO rejectRequest(UUID requestId, UUID callerId, String callerRole, String callerDept, String reason) {
        String normalizedRole = (callerRole != null) ? callerRole.toUpperCase().replace("ROLE_", "") : "";
        log.info("Rejecting access request ID: {} by caller: {} (role: {}, reason: {})", requestId, callerId, normalizedRole, reason);

        RoleApprovalRequest req = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found with ID: " + requestId));

        if (req.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Access request is already " + req.getStatus());
        }

        // Hierarchy Enforcement:
        if (req.getRequestedRole() == Role.ROLE_MANAGER && !"ADMIN".equals(normalizedRole)) {
            throw new SecurityException("Only an Admin can reject Manager access requests.");
        }
        if (req.getRequestedRole() == Role.ROLE_AGENT && !"ADMIN".equals(normalizedRole) && !"MANAGER".equals(normalizedRole)) {
            throw new SecurityException("Only Managers or Admins can reject Agent access requests.");
        }

        User caller = (callerId != null) ? userRepository.findById(callerId).orElse(null) : null;
        req.setStatus(ApprovalStatus.REJECTED);
        req.setReviewedBy(caller);
        req.setReviewedAt(LocalDateTime.now());
        req.setRejectionReason(reason != null && !reason.isBlank() ? reason.trim() : "Rejected by reviewer");

        RoleApprovalRequest savedReq = requestRepository.save(req);
        log.info("Successfully rejected request {} for user '{}'", requestId, req.getUser().getEmail());

        return AccessRequestDTO.fromEntity(savedReq);
    }

    @Transactional(readOnly = true)
    public AccessRequestDTO getMyPendingRequest(UUID userId) {
        if (userId == null) return null;
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return null;

        return requestRepository.findTopByUserAndStatusOrderByCreatedAtDesc(user, ApprovalStatus.PENDING)
                .map(AccessRequestDTO::fromEntity)
                .orElse(null);
    }

    @Transactional
    public AccessRequestDTO createOrUpdateRequest(UUID userId, Role requestedRole, String department) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + userId));

        Optional<RoleApprovalRequest> existing = requestRepository
                .findTopByUserAndStatusOrderByCreatedAtDesc(user, ApprovalStatus.PENDING);

        RoleApprovalRequest req = existing.orElseGet(() -> RoleApprovalRequest.builder().user(user).build());
        req.setRequestedRole(requestedRole);
        req.setDepartment(department != null && !department.isBlank() ? department.trim() : "IT");
        req.setStatus(ApprovalStatus.PENDING);
        RoleApprovalRequest saved = requestRepository.save(req);

        log.info("Created or updated access request for user '{}': requestedRole={}, dept={}",
                user.getEmail(), requestedRole, department);

        return AccessRequestDTO.fromEntity(saved);
    }
}
