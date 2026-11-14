package smartdesk.API_Gateway.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smartdesk.API_Gateway.dto.ApiResponse;
import smartdesk.API_Gateway.dto.AuthResponse;
import smartdesk.API_Gateway.dto.OtpRequestDTO;
import smartdesk.API_Gateway.dto.OtpVerifyDTO;
import smartdesk.API_Gateway.dto.RegisterRequestDTO;
import smartdesk.API_Gateway.dto.UserDTO;
import smartdesk.API_Gateway.entity.User;
import smartdesk.API_Gateway.repository.UserRepository;
import smartdesk.API_Gateway.security.JwtUtil;
import smartdesk.API_Gateway.service.OtpAuthService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final OtpAuthService otpAuthService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    // --- Login OTP Endpoints ---

    @PostMapping("/otp/request")
    public ResponseEntity<ApiResponse> requestOtp(@Valid @RequestBody OtpRequestDTO requestDTO) {
        log.info("REST request to trigger login OTP for: {}", requestDTO.getEmail());
        otpAuthService.requestOtp(requestDTO.getEmail());
        return ResponseEntity.ok(ApiResponse.ok("If an account exists with this email, a verification code has been sent."));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerifyDTO verifyDTO) {
        log.info("REST request to verify login OTP for: {}", verifyDTO.getEmail());
        User user = otpAuthService.verifyOtp(verifyDTO.getEmail(), verifyDTO.getOtp());
        String token = jwtUtil.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token, UserDTO.fromEntity(user)));
    }

    // --- Registration OTP Endpoints ---

    @PostMapping("/register/request")
    public ResponseEntity<ApiResponse> requestRegistrationOtp(@Valid @RequestBody RegisterRequestDTO requestDTO) {
        log.info("REST request to trigger registration OTP for: {}", requestDTO.getEmail());
        otpAuthService.requestRegistrationOtp(requestDTO);
        return ResponseEntity.ok(ApiResponse.ok("Verification code sent to your email."));
    }

    @PostMapping("/register/verify")
    public ResponseEntity<AuthResponse> verifyRegistrationOtp(@Valid @RequestBody OtpVerifyDTO verifyDTO) {
        log.info("REST request to verify registration OTP for: {}", verifyDTO.getEmail());
        User user = otpAuthService.verifyRegistrationOtp(verifyDTO.getEmail(), verifyDTO.getOtp());
        String token = jwtUtil.generateToken(user);
        return ResponseEntity.ok(new AuthResponse(token, UserDTO.fromEntity(user)));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(
            @RequestHeader(value = "X-User-Id", required = false) String rawUserId) {
        if (rawUserId == null || rawUserId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: User session required"));
        }
        try {
            UUID userId = UUID.fromString(rawUserId.trim());
            return userRepository.findById(userId)
                    .map(u -> {
                        String freshToken = jwtUtil.generateToken(u);
                        return ResponseEntity.ok(new AuthResponse(freshToken, UserDTO.fromEntity(u)));
                    })
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
