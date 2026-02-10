package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.service.DeduplicationService;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;

@RestController
@RequestMapping("/api/deduplication")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DeduplicationController {
    
    private final DeduplicationService deduplicationService;
    
    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<DeduplicationService.DeduplicationConfig>> getDeduplicationRules() {
        return ResponseUtil.success(deduplicationService.getDeduplicationConfig());
    }
    
    
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/rules")
    public ResponseEntity<ApiResponse<DeduplicationService.DeduplicationConfig>> updateDeduplicationRules(
            @RequestBody Map<String, Boolean> request) {
        
        DeduplicationService.DeduplicationConfig config = new DeduplicationService.DeduplicationConfig(
                request.getOrDefault("useEmail", true),
                request.getOrDefault("usePhone", true),
                request.getOrDefault("useAadhar", true)
        );
        
        DeduplicationService.DeduplicationConfig updated = deduplicationService.updateDeduplicationConfig(config);
        return ResponseUtil.success(updated, "Deduplication rules updated successfully");
    }
    
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<DeduplicationService.DeduplicationStats>> executeDeduplication(
            @RequestBody(required = false) Map<String, Boolean> config) {
        
        DeduplicationService.DeduplicationConfig overrideConfig = null;
        if (config != null && !config.isEmpty()) {
            overrideConfig = new DeduplicationService.DeduplicationConfig(
                    config.getOrDefault("useEmail", true),
                    config.getOrDefault("usePhone", true),
                    config.getOrDefault("useAadhar", true)
            );
        }
        
        DeduplicationService.DeduplicationStats stats = deduplicationService.executeDeduplication(overrideConfig);
        return ResponseUtil.success(stats, "Deduplication completed successfully");
    }
    
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DeduplicationService.DeduplicationStatsInfo>> getDeduplicationStats() {
        return ResponseUtil.success(deduplicationService.getDeduplicationStats());
    }
}
