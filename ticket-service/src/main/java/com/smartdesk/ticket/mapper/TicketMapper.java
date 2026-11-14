package com.smartdesk.ticket.mapper;

import com.smartdesk.ticket.client.UserDirectory;
import com.smartdesk.ticket.dto.TicketResponse;
import com.smartdesk.ticket.dto.client.UserContact;
import com.smartdesk.ticket.entity.AiAnalysis;
import com.smartdesk.ticket.entity.Department;
import com.smartdesk.ticket.entity.Priority;
import com.smartdesk.ticket.entity.Ticket;
import com.smartdesk.ticket.entity.TicketAssignment;
import com.smartdesk.ticket.entity.TriageSource;
import com.smartdesk.ticket.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Same JSON as before: names come from the auth service, parentTicketId from the incident rules. */
@Component
@RequiredArgsConstructor
public class TicketMapper {

    private final UserDirectory users;
    private final IncidentService incidents;

    public TicketResponse toResponse(Ticket t) {
        AiAnalysis a = t.getAnalysis();          // always present: created with the ticket
        Department category = (a != null && a.getCategory() != null) ? a.getCategory() : t.getDepartmentName();
        Priority priority = (a != null && a.getPriority() != null) ? a.getPriority() : Priority.MEDIUM;
        String summary = (a != null && a.getAiSummary() != null) ? a.getAiSummary() : t.getTitle();
        TriageSource source = (a != null && a.getSource() != null) ? a.getSource() : TriageSource.FALLBACK;
        TicketAssignment as = t.getAssignment(); // null until claimed/assigned
        UUID agent = as == null ? null : as.getAgentId();
        return new TicketResponse(t.getTicketId(), t.getTitle(), t.getDescription(), t.getStatus(),
                t.getDepartmentName(), category, priority, summary, source,
                t.getCreatedBy(), name(t.getCreatedBy()), agent, name(agent),
                incidents.incidentIdOf(t), t.getReportCount(), t.getCreatedAt(), t.getResolvedAt());
    }

    private String name(UUID userId) {
        UserContact u = userId == null ? null : users.find(userId);
        return u == null ? null : u.name();
    }
}
