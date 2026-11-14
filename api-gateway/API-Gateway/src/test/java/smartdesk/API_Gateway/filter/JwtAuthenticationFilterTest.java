package smartdesk.API_Gateway.filter;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import smartdesk.API_Gateway.security.JwtUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private GatewayFilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        lenient().when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void testPublicEndpoint_BypassesFilter() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/otp/request").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain, times(1)).filter(exchange);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void testOptionsPreflight_BypassesFilter() {
        MockServerHttpRequest request = MockServerHttpRequest.options("/api/v1/tickets/123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain, times(1)).filter(exchange);
        verifyNoInteractions(jwtUtil);
    }

    @Test
    void testSecureEndpoint_MissingAuthorizationHeader_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/tickets/123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any());
    }

    @Test
    void testSecureEndpoint_InvalidAuthorizationHeaderFormat_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/tickets/123")
                .header(HttpHeaders.AUTHORIZATION, "Basic 123456")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any());
    }

    @Test
    void testSecureEndpoint_InvalidJwtToken_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/tickets/123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.token")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(jwtUtil.validateToken("invalid.jwt.token")).thenReturn(false);

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any());
    }

    @Test
    void testSecureEndpoint_ValidJwtToken_MutatesHeadersAndProceeds() {
        String token = "valid.jwt.token";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/tickets/123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Claims mockClaims = mock(Claims.class);
        when(mockClaims.get("userId", String.class)).thenReturn("user-uuid-123");
        when(mockClaims.get("role", String.class)).thenReturn("ROLE_AGENT");
        when(mockClaims.getSubject()).thenReturn("agent@smartdesk.com");

        when(jwtUtil.validateToken(token)).thenReturn(true);
        when(jwtUtil.extractAllClaims(token)).thenReturn(mockClaims);

        when(filterChain.filter(any())).thenAnswer(invocation -> {
            ServerWebExchange mutatedExchange = invocation.getArgument(0);
            assertEquals("user-uuid-123", mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id"));
            assertEquals("agent@smartdesk.com", mutatedExchange.getRequest().getHeaders().getFirst("X-User-Email"));
            assertEquals("ROLE_AGENT", mutatedExchange.getRequest().getHeaders().getFirst("X-User-Role"));
            return Mono.empty();
        });

        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
                .verifyComplete();

        verify(filterChain, times(1)).filter(any());
    }
}
