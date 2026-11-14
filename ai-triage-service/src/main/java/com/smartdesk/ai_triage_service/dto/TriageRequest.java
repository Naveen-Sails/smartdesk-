package com.smartdesk.ai_triage_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TriageRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;

    private List<String> knownNames;

    public TriageRequest() {
    }

    public TriageRequest(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public TriageRequest(String title, String description, List<String> knownNames) {
        this.title = title;
        this.description = description;
        this.knownNames = knownNames;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getKnownNames() {
        return knownNames;
    }

    public void setKnownNames(List<String> knownNames) {
        this.knownNames = knownNames;
    }
}
