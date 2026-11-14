package smartdesk.API_Gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smartdesk.API_Gateway.entity.Role;
import smartdesk.API_Gateway.entity.User;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("mySecretKeyForTestingJwtMustBeAtLeast256BitsLong1234567890", 3600000L);

        testUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("John Doe")
                .email("john.doe@smartdesk.com")
                .role(Role.ROLE_EMPLOYEE)
                .department("Engineering")
                .isRootAdmin(false)
                .isActive(true)
                .build();
    }

    @Test
    void testGenerateAndValidateToken_Success() {
        String token = jwtUtil.generateToken(testUser);

        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));
        assertEquals("john.doe@smartdesk.com", jwtUtil.extractEmail(token));
        assertEquals(testUser.getId().toString(), jwtUtil.extractUserId(token));
        assertEquals(Role.ROLE_EMPLOYEE.name(), jwtUtil.extractRole(token));
    }

    @Test
    void testValidateToken_InvalidToken() {
        assertFalse(jwtUtil.validateToken("invalid.token.here"));
    }

    @Test
    void testValidateToken_ExpiredToken() {
        JwtUtil expiredJwtUtil = new JwtUtil("mySecretKeyForTestingJwtMustBeAtLeast256BitsLong1234567890", -1000L);
        String token = expiredJwtUtil.generateToken(testUser);

        assertFalse(jwtUtil.validateToken(token));
    }
}
