package smartdesk.API_Gateway.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import smartdesk.API_Gateway.dto.OtpRequestDTO;
import smartdesk.API_Gateway.dto.OtpVerifyDTO;
import smartdesk.API_Gateway.entity.Role;
import smartdesk.API_Gateway.entity.User;
import smartdesk.API_Gateway.exception.GlobalExceptionHandler;
import smartdesk.API_Gateway.security.JwtUtil;
import smartdesk.API_Gateway.service.OtpAuthService;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private OtpAuthService otpAuthService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthController authController;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(authController)
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testRequestOtp_Success() {
        OtpRequestDTO request = new OtpRequestDTO("user@smartdesk.com");
        doNothing().when(otpAuthService).requestOtp("user@smartdesk.com");

        webTestClient.post()
                .uri("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo(200)
                .jsonPath("$.message").isEqualTo("If an account exists with this email, a verification code has been sent.");

        verify(otpAuthService).requestOtp("user@smartdesk.com");
    }

    @Test
    void testVerifyOtp_Success() {
        OtpVerifyDTO verifyDTO = new OtpVerifyDTO("user@smartdesk.com", "123456");

        User user = User.builder()
                .id(UUID.randomUUID())
                .fullName("Test User")
                .email("user@smartdesk.com")
                .role(Role.ROLE_EMPLOYEE)
                .department("IT")
                .isRootAdmin(false)
                .isActive(true)
                .build();

        when(otpAuthService.verifyOtp("user@smartdesk.com", "123456")).thenReturn(user);
        when(jwtUtil.generateToken(user)).thenReturn("mock.jwt.token");

        webTestClient.post()
                .uri("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("mock.jwt.token")
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.user.email").isEqualTo("user@smartdesk.com")
                .jsonPath("$.user.role").isEqualTo("ROLE_EMPLOYEE");

        verify(otpAuthService).verifyOtp("user@smartdesk.com", "123456");
        verify(jwtUtil).generateToken(user);
    }

    @Test
    void testRequestRegistrationOtp_Success() {
        smartdesk.API_Gateway.dto.RegisterRequestDTO request = smartdesk.API_Gateway.dto.RegisterRequestDTO.builder()
                .fullName("New Employee")
                .email("new@smartdesk.com")
                .role(Role.ROLE_EMPLOYEE)
                .department("IT")
                .build();
        doNothing().when(otpAuthService).requestRegistrationOtp(any());

        webTestClient.post()
                .uri("/api/v1/auth/register/request")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo(200)
                .jsonPath("$.message").isEqualTo("Verification code sent to your email.");

        verify(otpAuthService).requestRegistrationOtp(any());
    }

    @Test
    void testVerifyRegistrationOtp_Success() {
        OtpVerifyDTO verifyDTO = new OtpVerifyDTO("new@smartdesk.com", "654321");

        User user = User.builder()
                .id(UUID.randomUUID())
                .fullName("New Employee")
                .email("new@smartdesk.com")
                .role(Role.ROLE_EMPLOYEE)
                .department("IT")
                .isRootAdmin(false)
                .isActive(true)
                .build();

        when(otpAuthService.verifyRegistrationOtp("new@smartdesk.com", "654321")).thenReturn(user);
        when(jwtUtil.generateToken(user)).thenReturn("mock.reg.jwt");

        webTestClient.post()
                .uri("/api/v1/auth/register/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(verifyDTO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("mock.reg.jwt")
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.user.email").isEqualTo("new@smartdesk.com");

        verify(otpAuthService).verifyRegistrationOtp("new@smartdesk.com", "654321");
        verify(jwtUtil).generateToken(user);
    }
}
