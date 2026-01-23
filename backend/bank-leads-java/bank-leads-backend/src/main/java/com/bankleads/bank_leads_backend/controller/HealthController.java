package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class HealthController {
    
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "ok");
        data.put("timestamp", LocalDateTime.now().toString());
        return ResponseUtil.success(data);
    }
}
