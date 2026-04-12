package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.model.AssignmentRule;
import com.bankleads.bank_leads_backend.repository.AssignmentRuleRepository;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assignment-rules")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AssignmentRuleController {

    private final AssignmentRuleRepository assignmentRuleRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AssignmentRule>>> listRules() {
        return ResponseUtil.success(assignmentRuleRepository.findAllByOrderByPriorityAsc());
    }

    /**
     * Replace all assignment rules (ordered by {@link AssignmentRule#getPriority()}).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public ResponseEntity<ApiResponse<List<AssignmentRule>>> replaceRules(@RequestBody List<AssignmentRule> rules) {
        assignmentRuleRepository.deleteAll();
        if (rules != null) {
            for (AssignmentRule r : rules) {
                r.setId(null);
            }
            assignmentRuleRepository.saveAll(rules);
        }
        return ResponseUtil.success(assignmentRuleRepository.findAllByOrderByPriorityAsc(), "Assignment rules saved");
    }
}
