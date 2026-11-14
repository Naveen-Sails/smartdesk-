package com.smartdesk.ai_triage_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class LlmConfig {

    @Value("${groq.api.url:https://api.groq.com/openai/v1}")
    private String groqUrl;

    @Value("${groq.api.key:mock-key}")
    private String groqApiKey;

    @Bean
    public RestClient groqRestClient() {

        return RestClient.builder()
                .baseUrl(groqUrl)
                .defaultHeader(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + groqApiKey
                )
                .defaultHeader(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }
}
