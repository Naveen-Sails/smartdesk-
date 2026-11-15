package smartdesk.API_Gateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import smartdesk.API_Gateway.entity.Role;
import smartdesk.API_Gateway.entity.RoleApprovalRequest;
import smartdesk.API_Gateway.entity.User;
import smartdesk.API_Gateway.repository.RoleApprovalRequestRepository;
import smartdesk.API_Gateway.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Initializes root administrator if absent and purges hardcoded dummy demo accounts,
 * ensuring all department staff and manager listings are 100% dynamically registered users.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleApprovalRequestRepository roleApprovalRequestRepository;

    private static final List<String> DUMMY_EMAILS = List.of(
            "manager@smartdesk.com",
            "hr.manager@smartdesk.com",
            "facilities.manager@smartdesk.com",
            "java.manager@smartdesk.com",
            "dotnet.manager@smartdesk.com",
            "ai.manager@smartdesk.com",
            "agent@smartdesk.com",
            "hr.agent@smartdesk.com",
            "facilities.agent@smartdesk.com",
            "java.agent@smartdesk.com",
            "dotnet.agent@smartdesk.com",
            "ai.agent@smartdesk.com",
            "employee@smartdesk.com"
    );

    @Override
    public void run(String... args) {
        try {
            // 1. Purge all dummy demo accounts from the database
            for (String email : DUMMY_EMAILS) {
                userRepository.findByEmailIgnoreCase(email).ifPresent(user -> {
                    try {
                        List<RoleApprovalRequest> reqs =
                                roleApprovalRequestRepository.findByUserOrderByCreatedAtDesc(user);
                        if (!reqs.isEmpty()) {
                            roleApprovalRequestRepository.deleteAll(reqs);
                        }
                        userRepository.delete(user);
                        log.info("Purged dummy demo user from database: {}", email);
                    } catch (Exception ex) {
                        user.setIsActive(false);
                        userRepository.save(user);
                        log.info("Deactivated dummy demo user: {}", email);
                    }
                });
            }

            // 2. Ensure strictly ONE Root Admin account exists (pre-seeded: admin@sailssoftware.com)
            String soleAdminEmail = "admin@sailssoftware.com";

            // Migrate legacy adminsails@gmail.com to admin@sailssoftware.com if present
            userRepository.findByEmailIgnoreCase("adminsails@gmail.com").ifPresent(legacyAdmin -> {
                if (userRepository.findByEmailIgnoreCase(soleAdminEmail).isEmpty()) {
                    legacyAdmin.setEmail(soleAdminEmail);
                    legacyAdmin.setRole(Role.ROLE_ADMIN);
                    legacyAdmin.setIsRootAdmin(true);
                    legacyAdmin.setIsActive(true);
                    userRepository.save(legacyAdmin);
                    log.info("Migrated legacy root admin from adminsails@gmail.com to {}", soleAdminEmail);
                } else {
                    userRepository.delete(legacyAdmin);
                }
            });

            seedUser(soleAdminEmail, "Moka Naveen", Role.ROLE_ADMIN, "Engineering", true);

            // 3. Demote or reassign any duplicate admin accounts so exactly ONE admin exists
            List<User> otherAdmins = userRepository.findAll().stream()
                    .filter(u -> !soleAdminEmail.equalsIgnoreCase(u.getEmail()) &&
                            (u.getRole() == Role.ROLE_ADMIN || Boolean.TRUE.equals(u.getIsRootAdmin())))
                    .toList();
            for (User otherAdmin : otherAdmins) {
                otherAdmin.setRole(Role.ROLE_EMPLOYEE);
                otherAdmin.setIsRootAdmin(false);
                otherAdmin.setUpdatedAt(LocalDateTime.now());
                userRepository.save(otherAdmin);
                log.info("Demoted duplicate admin account '{}' to ROLE_EMPLOYEE to enforce single admin role", otherAdmin.getEmail());
            }

        } catch (Exception ex) {
            log.warn("Database user initialization / purge encountered: {}", ex.getMessage());
        }
    }

    private void seedUser(String email, String fullName, Role role, String dept, boolean isRootAdmin) {
        String normalizedEmail = email.toLowerCase().trim();
        java.util.Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existingUserOpt.isPresent()) {
            User existing = existingUserOpt.get();
            existing.setFullName(fullName);
            existing.setRole(role);
            existing.setDepartment(dept != null ? dept : "Engineering");
            existing.setIsRootAdmin(isRootAdmin);
            existing.setIsActive(true);
            existing.setUpdatedAt(LocalDateTime.now());
            userRepository.save(existing);
            log.info("Updated root administrator: {} ({})", normalizedEmail, role);

            // Clean up any pending role requests for this admin account
            try {
                List<RoleApprovalRequest> reqs = roleApprovalRequestRepository.findByUserOrderByCreatedAtDesc(existing);
                if (!reqs.isEmpty()) {
                    roleApprovalRequestRepository.deleteAll(reqs);
                    log.info("Cleared prior role requests for seeded admin: {}", normalizedEmail);
                }
            } catch (Exception ex) {
                log.warn("Could not clear role requests for {}: {}", normalizedEmail, ex.getMessage());
            }
        } else {
            User user = User.builder()
                    .email(normalizedEmail)
                    .fullName(fullName)
                    .role(role)
                    .department(dept != null ? dept : "Engineering")
                    .isRootAdmin(isRootAdmin)
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            userRepository.save(user);
            log.info("Initialized root administrator: {} ({})", normalizedEmail, role);
        }
    }
}
