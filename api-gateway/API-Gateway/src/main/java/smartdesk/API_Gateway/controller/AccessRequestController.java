package smartdesk.API_Gateway.controller;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smartdesk.API_Gateway.dto.AccessRequestDTO;
import smartdesk.API_Gateway.dto.RoleApprovalDecisionDTO;
import smartdesk.API_Gateway.dto.StaffMemberDTO;
import smartdesk.API_Gateway.entity.Role;
import smartdesk.API_Gateway.entity.User;
import smartdesk.API_Gateway.repository.UserRepository;
import smartdesk.API_Gateway.security.JwtUtil;
import smartdesk.API_Gateway.service.AccessRequestService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access-requests")
@RequiredArgsConstructor
@Slf4j
public class AccessRequestController {

    private final AccessRequestService accessRequestService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    private User resolveCaller(String rawUserId, String authHeader) {
        if (rawUserId != null && !rawUserId.isBlank()) {
            try {
                return userRepository.findById(UUID.fromString(rawUserId.trim())).orElse(null);
            } catch (Exception ignored) {}
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                if (jwtUtil.validateToken(token)) {
                    String idStr = jwtUtil.extractUserId(token);
                    if (idStr != null && !idStr.isBlank()) {
                        return userRepository.findById(UUID.fromString(idStr.trim())).orElse(null);
                    }
                    String email = jwtUtil.extractEmail(token);
                    if (email != null && !email.isBlank()) {
                        return userRepository.findByEmailIgnoreCase(email).orElse(null);
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to extract caller from Authorization header: {}", ex.getMessage());
            }
        }
        return null;
    }

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingRequests(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @RequestHeader(value = "X-User-Role", required = false) String rawRole,
            @RequestHeader(value = "X-User-Dept", required = false) String rawDept,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        User caller = resolveCaller(rawUserId, authHeader);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: User session required"));
        }

        String role = (caller.getRole() != null) ? caller.getRole().name() : (rawRole != null ? rawRole : "EMPLOYEE");
        String dept = (caller.getDepartment() != null) ? caller.getDepartment() : (rawDept != null ? rawDept : "IT");

        try {
            List<AccessRequestDTO> requests = accessRequestService.getPendingRequests(caller.getId(), role, dept);
            return ResponseEntity.ok(requests);
        } catch (Exception ex) {
            log.error("Error fetching pending access requests: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveRequest(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @RequestHeader(value = "X-User-Role", required = false) String rawRole,
            @RequestHeader(value = "X-User-Dept", required = false) String rawDept,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        User caller = resolveCaller(rawUserId, authHeader);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: User session required"));
        }

        String role = (caller.getRole() != null) ? caller.getRole().name() : rawRole;
        String dept = (caller.getDepartment() != null) ? caller.getDepartment() : rawDept;

        try {
            AccessRequestDTO approved = accessRequestService.approveRequest(id, caller.getId(), role, dept);
            return ResponseEntity.ok(approved);
        } catch (SecurityException ex) {
            log.warn("Permission denied for approving request {}: {}", id, ex.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error approving request {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to approve request: " + ex.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectRequest(
            @PathVariable UUID id,
            @RequestBody(required = false) RoleApprovalDecisionDTO decision,
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @RequestHeader(value = "X-User-Role", required = false) String rawRole,
            @RequestHeader(value = "X-User-Dept", required = false) String rawDept,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        User caller = resolveCaller(rawUserId, authHeader);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: User session required"));
        }

        String role = (caller.getRole() != null) ? caller.getRole().name() : rawRole;
        String dept = (caller.getDepartment() != null) ? caller.getDepartment() : rawDept;

        try {
            String reason = (decision != null) ? decision.getReason() : null;
            AccessRequestDTO rejected = accessRequestService.rejectRequest(id, caller.getId(), role, dept, reason);
            return ResponseEntity.ok(rejected);
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error rejecting request {}: {}", id, ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reject request: " + ex.getMessage()));
        }
    }

    @GetMapping("/my-pending")
    public ResponseEntity<?> getMyPendingRequest(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        User caller = resolveCaller(rawUserId, authHeader);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: User session required"));
        }

        try {
            AccessRequestDTO myRequest = accessRequestService.getMyPendingRequest(caller.getId());
            return ResponseEntity.ok(myRequest != null ? myRequest : Map.of("hasPending", false));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/request")
    public ResponseEntity<?> submitRoleRequest(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        User caller = resolveCaller(rawUserId, authHeader);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: User session required"));
        }

        String rawRole = body.get("requestedRole");
        String dept = body.get("department");

        if (rawRole == null || rawRole.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "requestedRole is required"));
        }

        Role role;
        try {
            String normalized = rawRole.trim().toUpperCase();
            if (!normalized.startsWith("ROLE_")) normalized = "ROLE_" + normalized;
            role = Role.valueOf(normalized);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role specified: " + rawRole));
        }

        try {
            AccessRequestDTO created = accessRequestService.createOrUpdateRequest(caller.getId(), role, dept);
            return ResponseEntity.ok(created);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/staff")
    public ResponseEntity<?> getActiveStaff(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId,
            @RequestHeader(value = "X-User-Role", required = false) String rawRole,
            @RequestHeader(value = "X-User-Dept", required = false) String rawDept,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        User caller = resolveCaller(rawUserId, authHeader);
        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: User session required"));
        }

        Role callerRole = caller.getRole();
        if (callerRole != Role.ROLE_ADMIN && callerRole != Role.ROLE_MANAGER) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Forbidden: Only Admin or Managers can view department staff"));
        }

        List<User> staff;
        if (callerRole == Role.ROLE_ADMIN) {
            staff = userRepository.findActiveStaffByRoles(List.of(Role.ROLE_MANAGER, Role.ROLE_AGENT));
        } else {
            String dept = (caller.getDepartment() != null && !caller.getDepartment().isBlank())
                    ? caller.getDepartment().trim()
                    : (rawDept != null ? rawDept.trim() : "IT");
            staff = userRepository.findActiveStaffByRolesAndDepartment(List.of(Role.ROLE_AGENT), dept);
        }

        List<StaffMemberDTO> dtos = staff.stream()
                .map(StaffMemberDTO::fromEntity)
                .toList();

        return ResponseEntity.ok(dtos);
    }
}
