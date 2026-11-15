package smartdesk.API_Gateway.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import smartdesk.API_Gateway.client.NotificationClient;
import smartdesk.API_Gateway.entity.OtpVerification;
import smartdesk.API_Gateway.entity.Role;
import smartdesk.API_Gateway.entity.User;
import smartdesk.API_Gateway.exception.InvalidOtpException;
import smartdesk.API_Gateway.exception.RateLimitException;
import smartdesk.API_Gateway.repository.OtpVerificationRepository;
import smartdesk.API_Gateway.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpVerificationRepository otpVerificationRepository;

    @Spy
    private BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private OtpAuthServiceImpl otpAuthService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Alice Smith")
                .email("alice@sailssoftware.com")
                .role(Role.ROLE_EMPLOYEE)
                .department("Support")
                .isRootAdmin(false)
                .isActive(true)
                .build();
    }

    @Test
    void testRequestOtp_InvalidDomain_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> otpAuthService.requestOtp("user@otherdomain.com"));
        assertTrue(ex.getMessage().contains("@sailssoftware.com"));
    }

    @Test
    void testRequestOtp_UserNotFound_ThrowsIllegalArgumentException() {
        when(userRepository.findByEmailIgnoreCase("unknown@sailssoftware.com")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> otpAuthService.requestOtp("unknown@sailssoftware.com"));

        assertTrue(ex.getMessage().contains("Please sign up first"));
        verify(otpVerificationRepository, never()).save(any());
        verify(notificationClient, never()).sendOtpNotificationAsync(anyString(), anyString());
    }

    @Test
    void testRequestOtp_UserInactive_ThrowsIllegalArgumentException() {
        activeUser.setIsActive(false);
        when(userRepository.findByEmailIgnoreCase("alice@sailssoftware.com")).thenReturn(Optional.of(activeUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> otpAuthService.requestOtp("alice@sailssoftware.com"));

        assertTrue(ex.getMessage().contains("pending activation"));
        verify(otpVerificationRepository, never()).save(any());
        verify(notificationClient, never()).sendOtpNotificationAsync(anyString(), anyString());
    }

    @Test
    void testRequestOtp_ActiveUser_Success() {
        when(userRepository.findByEmailIgnoreCase("alice@sailssoftware.com")).thenReturn(Optional.of(activeUser));
        when(otpVerificationRepository.findTopByUserOrderByCreatedAtDesc(activeUser)).thenReturn(Optional.empty());
        when(otpVerificationRepository.save(any(OtpVerification.class))).thenAnswer(i -> i.getArgument(0));

        otpAuthService.requestOtp("alice@sailssoftware.com");

        verify(otpVerificationRepository).invalidateAllUnusedOtpsForUser(activeUser);

        ArgumentCaptor<OtpVerification> captor = ArgumentCaptor.forClass(OtpVerification.class);
        verify(otpVerificationRepository).save(captor.capture());
        OtpVerification savedOtp = captor.getValue();

        assertNotNull(savedOtp.getOtpHash());
        assertEquals(0, savedOtp.getAttemptCount());
        assertFalse(savedOtp.getIsUsed());
        assertTrue(savedOtp.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(4)));

        verify(notificationClient).sendOtpNotificationAsync(eq("alice@sailssoftware.com"), anyString());
    }

    @Test
    void testRequestOtp_RateLimitExceeded() {
        when(userRepository.findByEmailIgnoreCase("alice@sailssoftware.com")).thenReturn(Optional.of(activeUser));

        OtpVerification recentOtp = OtpVerification.builder()
                .createdAt(LocalDateTime.now().minusSeconds(30))
                .build();
        when(otpVerificationRepository.findTopByUserOrderByCreatedAtDesc(activeUser)).thenReturn(Optional.of(recentOtp));

        assertThrows(RateLimitException.class, () -> otpAuthService.requestOtp("alice@sailssoftware.com"));

        verify(notificationClient, never()).sendOtpNotificationAsync(anyString(), anyString());
    }

    @Test
    void testVerifyOtp_Success() {
        String rawOtp = "123456";
        String hashedOtp = passwordEncoder.encode(rawOtp);

        OtpVerification otpVerification = OtpVerification.builder()
                .user(activeUser)
                .otpHash(hashedOtp)
                .attemptCount(0)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .build();

        when(userRepository.findByEmailIgnoreCase("alice@sailssoftware.com")).thenReturn(Optional.of(activeUser));
        when(otpVerificationRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(activeUser))
                .thenReturn(Optional.of(otpVerification));

        User verifiedUser = otpAuthService.verifyOtp("alice@sailssoftware.com", rawOtp);

        assertNotNull(verifiedUser);
        assertEquals("alice@sailssoftware.com", verifiedUser.getEmail());
        assertTrue(otpVerification.getIsUsed());
        verify(otpVerificationRepository).saveAndFlush(otpVerification);
    }

    @Test
    void testVerifyOtp_IncorrectOtp_IncrementsAttemptCount() {
        String correctOtp = "123456";
        String wrongOtp = "654321";
        String hashedOtp = passwordEncoder.encode(correctOtp);

        OtpVerification otpVerification = OtpVerification.builder()
                .user(activeUser)
                .otpHash(hashedOtp)
                .attemptCount(0)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .build();

        when(userRepository.findByEmailIgnoreCase("alice@sailssoftware.com")).thenReturn(Optional.of(activeUser));
        when(otpVerificationRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(activeUser))
                .thenReturn(Optional.of(otpVerification));

        InvalidOtpException exception = assertThrows(InvalidOtpException.class,
                () -> otpAuthService.verifyOtp("alice@sailssoftware.com", wrongOtp));

        assertTrue(exception.getMessage().contains("Attempts remaining: 2"));
        assertEquals(1, otpVerification.getAttemptCount());
        assertFalse(otpVerification.getIsUsed());
        verify(otpVerificationRepository).saveAndFlush(otpVerification);
    }

    @Test
    void testVerifyOtp_ThirdFailedAttempt_LocksCode() {
        String correctOtp = "123456";
        String wrongOtp = "654321";
        String hashedOtp = passwordEncoder.encode(correctOtp);

        OtpVerification otpVerification = OtpVerification.builder()
                .user(activeUser)
                .otpHash(hashedOtp)
                .attemptCount(2)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .build();

        when(userRepository.findByEmailIgnoreCase("alice@sailssoftware.com")).thenReturn(Optional.of(activeUser));
        when(otpVerificationRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(activeUser))
                .thenReturn(Optional.of(otpVerification));

        InvalidOtpException exception = assertThrows(InvalidOtpException.class,
                () -> otpAuthService.verifyOtp("alice@sailssoftware.com", wrongOtp));

        assertTrue(exception.getMessage().contains("locked"));
        assertEquals(3, otpVerification.getAttemptCount());
        assertTrue(otpVerification.getIsUsed());
        verify(otpVerificationRepository).saveAndFlush(otpVerification);
    }

    @Test
    void testVerifyOtp_ExpiredOtp() {
        OtpVerification otpVerification = OtpVerification.builder()
                .user(activeUser)
                .otpHash(passwordEncoder.encode("123456"))
                .attemptCount(0)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .createdAt(LocalDateTime.now().minusMinutes(6))
                .build();

        when(userRepository.findByEmailIgnoreCase("alice@sailssoftware.com")).thenReturn(Optional.of(activeUser));
        when(otpVerificationRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(activeUser))
                .thenReturn(Optional.of(otpVerification));

        assertThrows(InvalidOtpException.class,
                () -> otpAuthService.verifyOtp("alice@sailssoftware.com", "123456"));

        assertTrue(otpVerification.getIsUsed());
        verify(otpVerificationRepository).saveAndFlush(otpVerification);
    }

    @Test
    void testRequestRegistrationOtp_NewUser_Success() {
        smartdesk.API_Gateway.dto.RegisterRequestDTO request = smartdesk.API_Gateway.dto.RegisterRequestDTO.builder()
                .fullName("Bob Ross")
                .email("bob@sailssoftware.com")
                .role(Role.ROLE_EMPLOYEE)
                .department("Facilities")
                .build();

        when(userRepository.findByEmailIgnoreCase("bob@sailssoftware.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(otpVerificationRepository.findTopByUserOrderByCreatedAtDesc(any(User.class))).thenReturn(Optional.empty());
        when(otpVerificationRepository.save(any(OtpVerification.class))).thenAnswer(i -> i.getArgument(0));

        assertDoesNotThrow(() -> otpAuthService.requestRegistrationOtp(request));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User createdUser = userCaptor.getValue();
        assertFalse(createdUser.getIsActive()); // Inactive pending verification
        assertEquals("bob@sailssoftware.com", createdUser.getEmail());

        verify(notificationClient).sendOtpNotificationAsync(eq("bob@sailssoftware.com"), anyString());
    }

    @Test
    void testRequestRegistrationOtp_AlreadyActiveUser_ThrowsException() {
        smartdesk.API_Gateway.dto.RegisterRequestDTO request = smartdesk.API_Gateway.dto.RegisterRequestDTO.builder()
                .fullName("Alice Smith")
                .email("alice@sailssoftware.com")
                .build();

        when(userRepository.findByEmailIgnoreCase("alice@sailssoftware.com")).thenReturn(Optional.of(activeUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> otpAuthService.requestRegistrationOtp(request));
        assertTrue(ex.getMessage().contains("already exists"));
        verify(notificationClient, never()).sendOtpNotificationAsync(anyString(), anyString());
    }

    @Test
    void testVerifyRegistrationOtp_Success() {
        User inactiveUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Bob Ross")
                .email("bob@sailssoftware.com")
                .role(Role.ROLE_EMPLOYEE)
                .isActive(false)
                .build();

        OtpVerification otpVerification = OtpVerification.builder()
                .user(inactiveUser)
                .otpHash(passwordEncoder.encode("654321"))
                .attemptCount(0)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmailIgnoreCase("bob@sailssoftware.com")).thenReturn(Optional.of(inactiveUser));
        when(otpVerificationRepository.findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(inactiveUser))
                .thenReturn(Optional.of(otpVerification));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = otpAuthService.verifyRegistrationOtp("bob@sailssoftware.com", "654321");

        assertTrue(result.getIsActive()); // Successfully activated
        assertTrue(otpVerification.getIsUsed());
        verify(userRepository).save(inactiveUser);
        verify(otpVerificationRepository).saveAndFlush(otpVerification);
    }
}
