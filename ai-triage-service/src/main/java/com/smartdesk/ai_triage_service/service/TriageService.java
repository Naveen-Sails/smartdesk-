package com.smartdesk.ai_triage_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdesk.ai_triage_service.dto.TriageRequest;
import com.smartdesk.ai_triage_service.dto.TriageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    private static final Set<String> DEPARTMENTS =
            Set.of("IT", "HR", "FACILITIES", "OPERATIONS", "OTHER");

    public TriageService(
            LlmService llmService,
            ObjectMapper objectMapper
    ) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    public TriageResponse classify(TriageRequest request) {
        if (llmService.isConfigured()) {
            try {
                return classifyWithLlm(request);
            } catch (Exception ex) {
                log.warn("Groq LLM call failed: {}. Falling back to heuristic classification.", ex.getMessage());
            }
        }
        return classifyHeuristic(request);
    }

    private TriageResponse classifyWithLlm(TriageRequest request) {
        String systemPrompt = """
                You are an AI ticket classification assistant
                for an internal company support system called SmartDesk.

                Your task is to analyze the employee's ticket title
                and description.

                Determine the correct department:

                IT         - Hardware, networking, operating systems,
                             email, printers, software, logins and general IT support.
                HR         - Payroll, salary, leave, attendance,
                             employee policies and recruitment.
                FACILITIES - Office maintenance, electricity,
                             air conditioning, furniture and cleaning.

                Determine priority using the actual impact and urgency:

                HIGH   - Critical issue, major work disruption,
                         security incident or multiple employees affected.
                MEDIUM - Significant individual issue affecting work,
                         but a workaround may exist.
                LOW    - Minor issue, general request or non-urgent problem.

                Generate a concise summary of the actual problem.

                Return ONLY a valid JSON object with exactly these fields:
                department, summary, priority.

                Department must be one of:
                IT, HR, FACILITIES.

                Priority must be one of:
                LOW, MEDIUM, HIGH.

                Do not include Markdown or additional explanations.
                """;

        String userPrompt = """
                Ticket title: %s

                Ticket description: %s
                """.formatted(
                request.getTitle(),
                request.getDescription()
        );

        String llmResponse = llmService.generate(
                systemPrompt,
                userPrompt
        );

        TriageResponse response;

        try {
            response = objectMapper.readValue(
                    llmResponse,
                    TriageResponse.class
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Could not parse AI classification response", e
            );
        }

        validateResponse(response);

        // Ensure category and priority normalization
        response.setCategory(response.getDepartment());
        if (response.getPriority() != null) {
            response.setPriority(response.getPriority().toUpperCase());
        }

        return response;
    }

    private TriageResponse classifyHeuristic(TriageRequest request) {
        String title = request.getTitle() != null ? request.getTitle().trim() : "";
        String desc = request.getDescription() != null ? request.getDescription().trim() : "";
        String text = (title + " " + desc).toLowerCase(Locale.ROOT);

        String dept = "IT";
        if (matchesAny(text, "salary", "payroll", "leave", "vacation", "pto", "holiday", "attendance", "policy", "bonus", "hr", "recruitment", "appraisal")) {
            dept = "HR";
        } else if (matchesAny(text, "clean", "desk", "chair", "furniture", "ac", "air condition", "leak", "washroom", "restroom", "light", "sanitory", "sanitary", "office maintenance")) {
            dept = "FACILITIES";
        } else if (matchesAny(text, "vpn", "wifi", "network", "password", "login", "laptop", "pc", "monitor", "software", "printer", "bug", "crash", "access", "internet")) {
            dept = "IT";
        }

        String priority = "MEDIUM";
        if (matchesAny(text, "urgent", "critical", "outage", "emergency", "fire", "security", "down", "blocker", "cannot work")) {
            priority = "HIGH";
        } else if (matchesAny(text, "minor", "typo", "suggestion", "non-urgent", "question", "inquiry")) {
            priority = "LOW";
        }

        String summary = title.length() > 80 ? title.substring(0, 80) : title;
        if (summary.isBlank()) {
            summary = desc.length() > 80 ? desc.substring(0, 80) : desc;
        }

        return new TriageResponse(dept, dept, summary, priority, null);
    }

    private boolean matchesAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private void validateResponse(TriageResponse response) {
        if (response.getDepartment() == null ||
                !DEPARTMENTS.contains(response.getDepartment().toUpperCase())) {
            response.setDepartment("IT");
        }

        if (response.getPriority() == null || response.getPriority().isBlank()) {
            response.setPriority("MEDIUM");
        }

        if (response.getSummary() == null || response.getSummary().isBlank()) {
            response.setSummary("Support request");
        }
    }
}
