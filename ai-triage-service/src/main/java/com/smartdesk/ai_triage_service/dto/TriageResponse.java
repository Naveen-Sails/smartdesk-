package com.smartdesk.ai_triage_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TriageResponse {

    private String department;
    private String category;
    private String summary;
    private String priority;
    private String issueKey;

    public TriageResponse() {
    }

    public TriageResponse(String department, String summary, String priority) {
        this.department = department;
        this.category = department;
        this.summary = summary;
        this.priority = priority != null ? priority.toUpperCase() : "MEDIUM";
        this.issueKey = generateIssueKey(summary != null ? summary : department);
    }

    public TriageResponse(String department, String category, String summary, String priority, String issueKey) {
        this.department = department;
        this.category = category != null ? category : department;
        this.summary = summary;
        this.priority = priority != null ? priority.toUpperCase() : "MEDIUM";
        this.issueKey = issueKey != null ? issueKey : generateIssueKey(summary);
    }

    public String getDepartment() {
        return department != null ? department : category;
    }

    public void setDepartment(String department) {
        this.department = department;
        if (this.category == null) {
            this.category = department;
        }
    }

    public String getCategory() {
        return category != null ? category : department;
    }

    public void setCategory(String category) {
        this.category = category;
        if (this.department == null) {
            this.department = category;
        }
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getPriority() {
        return priority != null ? priority.toUpperCase() : "MEDIUM";
    }

    public void setPriority(String priority) {
        this.priority = priority != null ? priority.toUpperCase() : "MEDIUM";
    }

    public String getIssueKey() {
        return issueKey != null ? issueKey : generateIssueKey(summary);
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }

    private static String generateIssueKey(String text) {
        if (text == null || text.isBlank()) return "general-issue";
        String clean = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").trim();
        String[] words = clean.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, words.length); i++) {
            if (!words[i].isBlank()) {
                if (sb.length() > 0) sb.append("-");
                sb.append(words[i]);
            }
        }
        return sb.length() > 0 ? sb.toString() : "general-issue";
    }
}
