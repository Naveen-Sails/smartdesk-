package smartdesk.API_Gateway.filter;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import smartdesk.API_Gateway.security.JwtUtil;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/v1/auth/",
            "/internal/",
            "/actuator/",
            "/favicon.ico"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Check if the current route is public or an OPTIONS preflight request
        if (HttpMethod.OPTIONS.equals(request.getMethod()) || isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Validate Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank()) {
            log.warn("Missing Authorization header for secure route: {}", path);
            return onError(exchange, "Missing Authorization Header", HttpStatus.UNAUTHORIZED);
        }

        if (!authHeader.startsWith("Bearer ")) {
            log.warn("Malformed Authorization header for route: {}", path);
            return onError(exchange, "Invalid Authorization Header Format", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7).trim();

        try {
            if (!jwtUtil.validateToken(token)) {
                log.warn("JWT validation failed (expired or invalid signature) for route: {}", path);
                return onError(exchange, "Invalid or Expired JWT Token", HttpStatus.UNAUTHORIZED);
            }

            Claims claims = jwtUtil.extractAllClaims(token);
            String userId = claims.get("userId", String.class);
            String rawRole = claims.get("role", String.class);
            String email = claims.getSubject();
            String fullName = claims.get("fullName", String.class);
            String department = claims.get("department", String.class);

            String role = (rawRole != null) ? rawRole.replace("ROLE_", "") : "EMPLOYEE";

            log.debug("JWT validated for user: {}, role: {}, injecting internal headers", email, role);

            // Mutate request with internal headers for downstream microservices
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId != null ? userId : "")
                    .header("X-User-Email", email != null ? email : "")
                    .header("X-User-Role", role)
                    .header("X-User-Dept", department != null && !department.isBlank() ? department : "IT")
                    .header("X-User-Name", fullName != null && !fullName.isBlank() ? fullName : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        } catch (Exception ex) {
            log.error("JWT token verification error: {}", ex.getMessage());
            return onError(exchange, "JWT Verification Error: " + ex.getMessage(), HttpStatus.UNAUTHORIZED);
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String json = String.format("{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}",
                LocalDateTime.now(), httpStatus.value(), httpStatus.getReasonPhrase(), err);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // High precedence before routing filters
    }
}
