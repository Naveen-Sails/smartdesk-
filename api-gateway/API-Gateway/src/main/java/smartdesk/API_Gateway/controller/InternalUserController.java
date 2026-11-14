package smartdesk.API_Gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import smartdesk.API_Gateway.dto.UserContactDTO;
import smartdesk.API_Gateway.entity.User;
import smartdesk.API_Gateway.repository.UserRepository;

import java.util.Map;
import java.util.UUID;

/**
 * Internal endpoint for other microservices (e.g. ticket-service) to look up
 * user names and emails by user ID, secured by shared X-Internal-Key.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Slf4j
public class InternalUserController {

    private final UserRepository userRepository;

    @Value("${smartdesk.internal-key:dev-internal-key-change-me}")
    private String configuredInternalKey;

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Internal-Key", required = false) String internalKey) {

        if (internalKey == null || !internalKey.equals(configuredInternalKey)) {
            log.warn("Unauthorized internal user lookup attempt for user: {}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: invalid or missing internal key"));
        }

        return userRepository.findById(id)
                .map(user -> ResponseEntity.ok(new UserContactDTO(user.getId(), user.getFullName(), user.getEmail())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
