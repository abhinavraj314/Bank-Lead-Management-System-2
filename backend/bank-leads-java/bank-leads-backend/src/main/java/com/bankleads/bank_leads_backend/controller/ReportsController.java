package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.dto.response.LeadSourceReportResponse;
import com.bankleads.bank_leads_backend.model.User;
import com.bankleads.bank_leads_backend.repository.UserRepository;
import com.bankleads.bank_leads_backend.service.LeadSourceReportService;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ReportsController {

    private final LeadSourceReportService leadSourceReportService;
    private final UserRepository userRepository;

    @GetMapping("/lead-sources")
    public ResponseEntity<ApiResponse<LeadSourceReportResponse>> getLeadSourceAnalytics(
            @RequestParam(required = false) String sourceId,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "day") String bucket,
            HttpServletRequest request
    ) {
        String currentUserId = getCurrentUserId(request);
        boolean isAdmin = isCurrentUserAdmin();

        LeadSourceReportResponse response = leadSourceReportService.getSourceAnalytics(
                sourceId,
                productId,
                from,
                to,
                bucket,
                currentUserId,
                isAdmin
        );
        return ResponseUtil.success(response, "Lead source analytics fetched successfully");
    }

    private String getCurrentUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String username) {
            User user = userRepository.findByUsername(username).orElse(null);
            return user != null ? user.getId() : null;
        }
        return null;
    }

    private boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }
}
