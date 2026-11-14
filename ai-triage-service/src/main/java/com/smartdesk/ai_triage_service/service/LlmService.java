package com.smartdesk.ai_triage_service.service;

import com.smartdesk.ai_triage_service.client.LlmClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    private final LlmClient llmClient;
    private final String apiKey;
    private final String model;

    public LlmService(
            LlmClient llmClient,
            @Value("${groq.api.key:mock-key}") String apiKey,
            @Value("${groq.api.model:openai/gpt-oss-20b}") String model
    ) {
        this.llmClient = llmClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean isConfigured() {
        return apiKey != null
                && !apiKey.isBlank()
                && !apiKey.equalsIgnoreCase("mock-key")
                && !apiKey.startsWith("${");
    }

    @SuppressWarnings("unchecked")
    public String generate(String systemPrompt, String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object")
        );

        Map<String, Object> response = llmClient.generate(
                "Bearer " + apiKey,
                requestBody
        );

        if (response == null || !response.containsKey("choices")) {
            throw new IllegalStateException("Empty or invalid response received from Groq");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("No choices returned from Groq");
        }

        Map<String, Object> firstChoice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
        if (message == null || !message.containsKey("content")) {
            throw new IllegalStateException("No message content returned from Groq");
        }

        return String.valueOf(message.get("content"));
    }
}
