package smartdesk.API_Gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class GatewayRoutesConfig {

    @Value("${ticket.service.url:${TICKET_SERVICE_URL:http://127.0.0.1:8081}}")
    private String ticketServiceUrl;

    @Value("${ai.triage.service.url:${AI_TRIAGE_SERVICE_URL:http://127.0.0.1:8084}}")
    private String aiTriageServiceUrl;

    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        log.info("Configuring Gateway route for Ticket Service pointing to: {}", ticketServiceUrl);
        log.info("Configuring Gateway route for AI Triage Service pointing to: {}", aiTriageServiceUrl);
        return builder.routes()
                .route("ticket-service-route", r -> r
                        .path("/api/v1/tickets", "/api/v1/tickets/**")
                        .uri(ticketServiceUrl))
                .route("ai-triage-route", r -> r
                        .path("/api/v1/triage", "/api/v1/triage/**")
                        .uri(aiTriageServiceUrl))
                .build();
    }
}
