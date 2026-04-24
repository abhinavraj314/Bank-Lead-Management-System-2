package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.model.WorkflowDefinition;
import com.bankleads.bank_leads_backend.repository.WorkflowDefinitionRepository;
import com.bankleads.bank_leads_backend.service.WorkflowService;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/workflows")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final WorkflowService workflowService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/default")
    public ResponseEntity<ApiResponse<WorkflowDefinition>> getDefault() {
        return ResponseUtil.success(workflowService.getDefaultWorkflowOrThrow());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/team/{teamId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTeamWorkflow(@PathVariable String teamId) {
        WorkflowDefinition effectiveDefault = workflowService.getDefaultWorkflowOrThrow();
        Optional<WorkflowDefinition> override = workflowService.findTeamWorkflow(teamId);
        Map<String, Object> out = Map.of(
                "key", workflowService.teamKey(teamId),
                "isOverride", override.isPresent(),
                "definition", override.orElse(effectiveDefault)
        );
        return ResponseUtil.success(out);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/team/{teamId}")
    public ResponseEntity<ApiResponse<WorkflowDefinition>> upsertTeamWorkflow(
            @PathVariable String teamId,
            @RequestBody Map<String, Object> body) {

        Object transitionsObj = body != null ? body.get("transitions") : null;
        if (!(transitionsObj instanceof List<?> list)) {
            return ResponseUtil.error("transitions is required", HttpStatus.BAD_REQUEST);
        }

        List<WorkflowDefinition.Transition> transitions;
        try {
            transitions = list.stream().map(item -> {
                if (!(item instanceof Map<?, ?> m)) {
                    throw new IllegalArgumentException("Each transition must be an object");
                }
                Object from = m.get("fromState");
                Object to = m.get("toState");
                if (from == null || to == null) {
                    throw new IllegalArgumentException("fromState and toState are required");
                }
                return WorkflowDefinition.Transition.builder()
                        .fromState(from.toString().trim().toUpperCase())
                        .toState(to.toString().trim().toUpperCase())
                        .build();
            }).toList();
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        String key = workflowService.teamKey(teamId);
        WorkflowDefinition def = workflowDefinitionRepository.findByKey(key)
                .orElse(WorkflowDefinition.builder().key(key).build());
        def.setTransitions(transitions);
        WorkflowDefinition saved = workflowDefinitionRepository.save(def);
        return ResponseUtil.success(saved, "Team workflow saved");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/team/{teamId}")
    public ResponseEntity<ApiResponse<Object>> deleteTeamWorkflow(@PathVariable String teamId) {
        String key = workflowService.teamKey(teamId);
        workflowDefinitionRepository.findByKey(key)
                .ifPresent(workflowDefinitionRepository::delete);
        return ResponseUtil.success(Map.of("deleted", true), "Team workflow reverted to default");
    }
}

