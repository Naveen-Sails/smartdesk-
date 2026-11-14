package com.smartdesk.ai_triage_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(
        name = "groq-client",
        url = "${groq.api.url}"
)
public interface LlmClient {

    @PostMapping("/chat/completions")
    Map<String, Object> generate(
            @RequestHeader("Authorization") String authorization,
            @RequestBody Map<String, Object> requestBody
    );
}
