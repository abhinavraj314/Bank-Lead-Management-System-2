package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.Lead;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Lead lifecycle transitions validated against configurable workflow ({@link WorkflowService}).
 */
@Service
@RequiredArgsConstructor
public class LeadStateService {

    private final WorkflowService workflowService;

    @SuppressWarnings("unused")
    public void updateStatus(Lead lead, Lead.LeadStatus newStatus, boolean isAdmin) {
        Lead.LeadStatus target = workflowService.normalizeTargetState(newStatus);
        Lead.LeadStatus currentNorm = workflowService.normalizeCurrentState(lead);

        if (!workflowService.isTransitionAllowed(currentNorm, target)) {
            throw new IllegalArgumentException(
                    String.format("Invalid state transition from %s to %s", currentNorm, target)
            );
        }

        // Persist canonical state on `state`; keep `status` in sync when it already exists (legacy docs).
        if (lead.getState() != null || lead.getStatus() != null) {
            lead.setState(target);
            if (lead.getStatus() != null) {
                lead.setStatus(target);
            }
        } else {
            lead.setState(target);
            lead.setStatus(target);
        }
        lead.setStatusUpdatedAt(LocalDateTime.now());
    }

    public boolean canUpdateState(Lead lead, String userId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        return lead.getAssignedUserId() != null && lead.getAssignedUserId().equals(userId);
    }

    public boolean canViewLead(Lead lead, String userId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        return lead.getAssignedUserId() != null && lead.getAssignedUserId().equals(userId);
    }
}
