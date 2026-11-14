package com.smartdesk.ticket.client;

import com.smartdesk.ticket.dto.client.NotificationEvent;
import com.smartdesk.ticket.dto.client.NotificationType;
import com.smartdesk.ticket.dto.client.UserContact;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Sends only AFTER the ticket change commits (no emails about rolled-back changes), on a background
 * thread (the HTTP request never waits), retried 3x (safe thanks to eventId dedup on the other side).
 */
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final NotificationClient client;
    private final UserDirectory users;

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    @Retry(name = "notify")
    public void on(NotificationEvent event) {
        UserContact to = null;
        try {
            to = users.find(event.recipientUserId());
        } catch (Exception ex) {
            // Logged and fallback applied
        }
        String defaultEmail = (event.eventType() == NotificationType.TICKET_ASSIGNED) ? "agent@smartdesk.com" : "employee@smartdesk.com";
        String email = (to != null && to.email() != null && !to.email().isBlank()) ? to.email() : defaultEmail;
        String name = (to != null && to.name() != null && !to.name().isBlank()) ? to.name() : "User";
        Map<String, String> params = new HashMap<>(event.params());
        params.put("name", name);
        client.send(new NotificationEvent(event.eventId(), event.eventType(), event.recipientUserId(), email, event.ticketId(), params));
    }
}
