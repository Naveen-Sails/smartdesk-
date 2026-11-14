package com.smartdesk.ai_triage_service.controller;

import com.smartdesk.ai_triage_service.dto.TriageRequest;
import com.smartdesk.ai_triage_service.dto.TriageResponse;
import com.smartdesk.ai_triage_service.service.TriageService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/triage", "/api/triage"})
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    @PostMapping
    public ResponseEntity<TriageResponse> classifyTicket(@Valid @RequestBody TriageRequest request) {
        TriageResponse response = triageService.classify(request);
        return ResponseEntity.ok(response);
    }
}
