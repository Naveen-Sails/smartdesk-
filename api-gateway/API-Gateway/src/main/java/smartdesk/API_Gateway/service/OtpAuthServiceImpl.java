package smartdesk.API_Gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartdesk.API_Gateway.client.NotificationClient;
import smartdesk.API_Gateway.dto.RegisterRequestDTO;
import smartdesk.API_Gateway.entity.ApprovalStatus;
import smartdesk.API_Gateway.entity.OtpVerification;
import smartdesk.API_Gateway.entity.Role;
import smartdesk.API_Gateway.entity.RoleApprovalRequest;
import smartdesk.API_Gateway.entity.User;
import smartdesk.API_Gateway.exception.InvalidOtpException;
import smartdesk.API_Gateway.exception.RateLimitException;
import smartdesk.API_Gateway.repository.OtpVerificationRepository;
import smartdesk.API_Gateway.repository.RoleApprovalRequestRepository;
import smartdesk.API_Gateway.repository.UserRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpAuthServiceImpl implements OtpAuthService {

    private final UserRepository userRepository;
    private final OtpVerificationRepository otpVerificationRepository;
    private final RoleApprovalRequestRepository roleApprovalRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationClient notificationClient;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void requestOtp(String email) {
        String sanitizedEmail = (email != null) ? email.trim().toLowerCase() : "";
        log.info("Processing OTP request for email: {}", sanitizedEmail);

        Optional<User> userOptional = userRepository.findByEmailIgnoreCase(sanitizedEmail);

        // Prevent email enumeration: return generic success if user does not exist or is inactive
        if (userOptional.isEmpty() || !Boolean.TRUE.equals(userOptional.get().getIsActive())) {
            log.info("User with email '{}' not found or inactive. Returning generic response.", sanitizedEmail);
            return;
        }

        User user = userOptional.get();

        // Rate limiting check: reject if last OTP was requested less than 60 seconds ago
        Optional<OtpVerification> lastOtpOpt = otpVerificationRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (lastOtpOpt.isPresent()) {
            LocalDateTime lastCreatedAt = lastOtpOpt.get().getCreatedAt();
            if (lastCreatedAt != null && lastCreatedAt.isAfter(LocalDateTime.now().minusSeconds(60))) {
                long secondsElapsed = Duration.between(lastCreatedAt, LocalDateTime.now()).getSeconds();
                long secondsRemaining = Math.max(1, 60 - secondsElapsed);
                log.warn("Rate limit triggered for email '{}'. Seconds remaining: {}", sanitizedEmail, secondsRemaining);
                throw new RateLimitException("Please wait " + secondsRemaining + " seconds before requesting another OTP.");
            }
        }

        // Invalidate all previous unused OTPs for this user
        otpVerificationRepository.invalidateAllUnusedOtpsForUser(user);

        // Generate 6-digit cryptographically secure OTP
        int otpNumber = 100000 + secureRandom.nextInt(900000);
        String otp = String.valueOf(otpNumber);

        // Print prominently in console for development/testing
        log.info("==================================================================");
        log.info(">> [LOCAL DEV OTP] Email: {} | Code: {} (Expires in 5 mins)", user.getEmail(), otp);
        log.info("==================================================================");

        // Hash with BCrypt
        String hashedOtp = passwordEncoder.encode(otp);

        // Save OtpVerification
        OtpVerification otpVerification = OtpVerification.builder()
                .user(user)
                .otpHash(hashedOtp)
                .attemptCount(0)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();

        otpVerificationRepository.save(otpVerification);
        log.info("Generated and stored new OTP verification record for user ID: {}", user.getId());

        // Dispatch async notification
        notificationClient.sendOtpNotificationAsync(user.getEmail(), otp);
    }

    @Override
    @Transactional
    public User verifyOtp(String email, String otp) {
        String sanitizedEmail = (email != null) ? email.trim().toLowerCase() : "";
        log.info("Verifying OTP for email: {}", sanitizedEmail);

        User user = userRepository.findByEmailIgnoreCase(sanitizedEmail)
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseThrow(() -> new InvalidOtpException("Invalid email or OTP."));

        OtpVerification otpVerification = otpVerificationRepository
                .findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new InvalidOtpException("No active OTP found. Please request a new OTP."));

        // Check if maximum attempts reached
        if (otpVerification.getAttemptCount() >= 3) {
            otpVerification.setIsUsed(true);
            otpVerificationRepository.save(otpVerification);
            log.warn("OTP verification rejected: max attempts exceeded for user: {}", sanitizedEmail);
            throw new InvalidOtpException("Maximum verification attempts exceeded. Please request a new OTP.");
        }

        // Check if OTP is expired
        if (LocalDateTime.now().isAfter(otpVerification.getExpiresAt())) {
            otpVerification.setIsUsed(true);
            otpVerificationRepository.save(otpVerification);
            log.warn("OTP verification rejected: OTP expired for user: {}", sanitizedEmail);
            throw new InvalidOtpException("OTP has expired. Please request a new OTP.");
        }

        // Check hash matching
        if (!passwordEncoder.matches(otp, otpVerification.getOtpHash())) {
            int attempts = otpVerification.getAttemptCount() + 1;
            otpVerification.setAttemptCount(attempts);
            if (attempts >= 3) {
                otpVerification.setIsUsed(true);
            }
            otpVerificationRepository.save(otpVerification);

            int remaining = 3 - attempts;
            log.warn("Incorrect OTP entered for user: {}. Attempts remaining: {}", sanitizedEmail, remaining);
            if (remaining <= 0) {
                throw new InvalidOtpException("Invalid OTP. Attempt limit exceeded. Code locked.");
            } else {
                throw new InvalidOtpException("Invalid OTP. Attempts remaining: " + remaining);
            }
        }

        // Mark OTP as used and save
        otpVerification.setIsUsed(true);
        otpVerificationRepository.save(otpVerification);
        log.info("OTP verification successful for user: {}", sanitizedEmail);

        return user;
    }

    @Override
    @Transactional
    public void requestRegistrationOtp(RegisterRequestDTO request) {
        String sanitizedEmail = (request.getEmail() != null) ? request.getEmail().trim().toLowerCase() : "";
        log.info("Processing registration OTP request for email: {}", sanitizedEmail);

        if (sanitizedEmail.isBlank()) {
            throw new IllegalArgumentException("Email is required for registration.");
        }

        Optional<User> existingUserOpt = userRepository.findByEmailIgnoreCase(sanitizedEmail);
        User user;

        // Only one admin exists in the system (pre-seeded). All public registrations are strictly EMPLOYEE.
        Role baseRole = Role.ROLE_EMPLOYEE;
        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            // If user is already active, reject registration
            if (Boolean.TRUE.equals(existingUser.getIsActive())) {
                log.warn("Registration rejected: email '{}' is already registered and active", sanitizedEmail);
                throw new IllegalArgumentException("An active account already exists with this email address. Please sign in instead.");
            }
            // User exists but inactive (e.g. unfinished prior registration): update profile details
            existingUser.setFullName(request.getFullName() != null ? request.getFullName().trim() : existingUser.getFullName());
            existingUser.setRole(baseRole);
            existingUser.setIsRootAdmin(false);
            existingUser.setDepartment(request.getDepartment());
            user = userRepository.save(existingUser);
            log.info("Updated existing inactive user record ID: {} for re-registration with base role {}", user.getId(), baseRole);
        } else {
            // Create pending user (inactive until OTP verified), strictly defaulting to EMPLOYEE
            User newUser = User.builder()
                    .fullName(request.getFullName() != null ? request.getFullName().trim() : "New User")
                    .email(sanitizedEmail)
                    .role(baseRole)
                    .department(request.getDepartment())
                    .isRootAdmin(false)
                    .isActive(false) // Pending activation
                    .build();
            user = userRepository.save(newUser);
            log.info("Created pending-activation user ID: {} with default role: {}", user.getId(), user.getRole());
        }

        // If user requested a privileged role (AGENT or MANAGER), submit a pending access request
        // Note: Admin cannot be requested via registration
        if (request.getRole() != null &&
                request.getRole() != Role.ROLE_EMPLOYEE &&
                request.getRole() != Role.ROLE_ADMIN) {
            Optional<RoleApprovalRequest> existingReqOpt = roleApprovalRequestRepository
                    .findTopByUserAndStatusOrderByCreatedAtDesc(user, ApprovalStatus.PENDING);
            RoleApprovalRequest approvalReq = existingReqOpt.orElseGet(() -> RoleApprovalRequest.builder()
                    .user(user)
                    .build());
            approvalReq.setRequestedRole(request.getRole());
            approvalReq.setDepartment(request.getDepartment() != null ? request.getDepartment().trim() : "IT");
            approvalReq.setStatus(ApprovalStatus.PENDING);
            roleApprovalRequestRepository.save(approvalReq);
            log.info("Created/Updated pending access request for user '{}': requestedRole={}, department={}",
                    user.getEmail(), approvalReq.getRequestedRole(), approvalReq.getDepartment());
        }

        // Rate limiting check: reject if last OTP was requested less than 60 seconds ago
        Optional<OtpVerification> lastOtpOpt = otpVerificationRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (lastOtpOpt.isPresent()) {
            LocalDateTime lastCreatedAt = lastOtpOpt.get().getCreatedAt();
            if (lastCreatedAt != null && lastCreatedAt.isAfter(LocalDateTime.now().minusSeconds(60))) {
                long secondsElapsed = Duration.between(lastCreatedAt, LocalDateTime.now()).getSeconds();
                long secondsRemaining = Math.max(1, 60 - secondsElapsed);
                log.warn("Registration rate limit triggered for email '{}'. Seconds remaining: {}", sanitizedEmail, secondsRemaining);
                throw new RateLimitException("Please wait " + secondsRemaining + " seconds before requesting another verification code.");
            }
        }

        // Invalidate prior unused OTPs
        otpVerificationRepository.invalidateAllUnusedOtpsForUser(user);

        // Generate 6-digit OTP
        int otpNumber = 100000 + secureRandom.nextInt(900000);
        String otp = String.valueOf(otpNumber);

        log.info("==================================================================");
        log.info(">> [LOCAL DEV REGISTRATION OTP] Email: {} | Code: {} (Expires in 5 mins)", user.getEmail(), otp);
        log.info("==================================================================");

        // Hash with BCrypt
        String hashedOtp = passwordEncoder.encode(otp);

        OtpVerification otpVerification = OtpVerification.builder()
                .user(user)
                .otpHash(hashedOtp)
                .attemptCount(0)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();

        otpVerificationRepository.save(otpVerification);

        // Dispatch async email notification
        notificationClient.sendOtpNotificationAsync(user.getEmail(), otp);
    }

    @Override
    @Transactional
    public User verifyRegistrationOtp(String email, String otp) {
        String sanitizedEmail = (email != null) ? email.trim().toLowerCase() : "";
        log.info("Verifying registration OTP for email: {}", sanitizedEmail);

        User user = userRepository.findByEmailIgnoreCase(sanitizedEmail)
                .orElseThrow(() -> new InvalidOtpException("Invalid email or verification code."));

        OtpVerification otpVerification = otpVerificationRepository
                .findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new InvalidOtpException("No active verification code found. Please request a new code."));

        // Check if maximum attempts reached
        if (otpVerification.getAttemptCount() >= 3) {
            otpVerification.setIsUsed(true);
            otpVerificationRepository.save(otpVerification);
            log.warn("Registration OTP rejected: max attempts exceeded for user: {}", sanitizedEmail);
            throw new InvalidOtpException("Maximum verification attempts exceeded. Please request a new verification code.");
        }

        // Check if OTP is expired
        if (LocalDateTime.now().isAfter(otpVerification.getExpiresAt())) {
            otpVerification.setIsUsed(true);
            otpVerificationRepository.save(otpVerification);
            log.warn("Registration OTP rejected: OTP expired for user: {}", sanitizedEmail);
            throw new InvalidOtpException("Verification code has expired. Please request a new code.");
        }

        // Check hash matching
        if (!passwordEncoder.matches(otp, otpVerification.getOtpHash())) {
            int attempts = otpVerification.getAttemptCount() + 1;
            otpVerification.setAttemptCount(attempts);
            if (attempts >= 3) {
                otpVerification.setIsUsed(true);
            }
            otpVerificationRepository.save(otpVerification);

            int remaining = 3 - attempts;
            log.warn("Incorrect registration OTP entered for user: {}. Attempts remaining: {}", sanitizedEmail, remaining);
            if (remaining <= 0) {
                throw new InvalidOtpException("Invalid verification code. Attempt limit exceeded. Code locked.");
            } else {
                throw new InvalidOtpException("Invalid verification code. Attempts remaining: " + remaining);
            }
        }

        // Mark OTP as used and activate the user
        otpVerification.setIsUsed(true);
        otpVerificationRepository.save(otpVerification);

        user.setIsActive(true);
        User activatedUser = userRepository.save(user);
        log.info("Registration OTP verification successful. User activated: {}", sanitizedEmail);

        return activatedUser;
    }

    @Override
    @Transactional
    public User registerUser(RegisterRequestDTO request) {
        String sanitizedEmail = request.getEmail().trim().toLowerCase();
        log.info("Registering new user with email: {}", sanitizedEmail);

        if (userRepository.existsByEmailIgnoreCase(sanitizedEmail)) {
            log.warn("Registration rejected: email '{}' already in use", sanitizedEmail);
            throw new IllegalArgumentException("User with email '" + sanitizedEmail + "' already exists");
        }

        Role baseRole = Boolean.TRUE.equals(request.getIsRootAdmin()) ? Role.ROLE_ADMIN : Role.ROLE_EMPLOYEE;
        User newUser = User.builder()
                .fullName(request.getFullName().trim())
                .email(sanitizedEmail)
                .role(baseRole)
                .department(request.getDepartment())
                .isRootAdmin(Boolean.TRUE.equals(request.getIsRootAdmin()))
                .isActive(true)
                .build();

        User savedUser = userRepository.save(newUser);
        log.info("Successfully registered user ID: {} with base role: {}", savedUser.getId(), savedUser.getRole());

        // Create pending access request if a higher role was requested
        if (request.getRole() != null && request.getRole() != Role.ROLE_EMPLOYEE && !Boolean.TRUE.equals(request.getIsRootAdmin())) {
            RoleApprovalRequest approvalReq = RoleApprovalRequest.builder()
                    .user(savedUser)
                    .requestedRole(request.getRole())
                    .department(request.getDepartment() != null ? request.getDepartment().trim() : "IT")
                    .status(ApprovalStatus.PENDING)
                    .build();
            roleApprovalRequestRepository.save(approvalReq);
            log.info("Created pending access request for user '{}': requestedRole={}", savedUser.getEmail(), request.getRole());
        }

        return savedUser;
    }
}
