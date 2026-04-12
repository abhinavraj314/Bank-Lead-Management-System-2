package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.model.WorkflowDefinition;
import com.bankleads.bank_leads_backend.repository.WorkflowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    public static final String DEFAULT_KEY = "DEFAULT";

    private final WorkflowDefinitionRepository workflowDefinitionRepository;

    /**
     * Effective lifecycle state for workflow logic. Legacy {@link Lead.LeadStatus#CLOSED}
     * is treated as {@link Lead.LeadStatus#NOT_CONVERTED}.
     */
    public Lead.LeadStatus normalizeCurrentState(Lead lead) {
        Lead.LeadStatus s = lead.getState() != null ? lead.getState() : lead.getStatus();
        if (s == null) {
            return Lead.LeadStatus.NEW;
        }
        if (s == Lead.LeadStatus.CLOSED) {
            return Lead.LeadStatus.NOT_CONVERTED;
        }
        if (s == Lead.LeadStatus.IN_PROGRESS) {
            return Lead.LeadStatus.CONTACTED;
        }
        if (s == Lead.LeadStatus.QUALIFIED) {
            return Lead.LeadStatus.PROPOSAL_SENT;
        }
        return s;
    }

    /**
     * Map incoming API value: CLOSED is accepted as alias for NOT_CONVERTED.
     */
    public Lead.LeadStatus normalizeTargetState(Lead.LeadStatus requested) {
        if (requested == Lead.LeadStatus.CLOSED) {
            return Lead.LeadStatus.NOT_CONVERTED;
        }
        if (requested == Lead.LeadStatus.IN_PROGRESS) {
            return Lead.LeadStatus.CONTACTED;
        }
        if (requested == Lead.LeadStatus.QUALIFIED) {
            return Lead.LeadStatus.PROPOSAL_SENT;
        }
        return requested;
    }

    public boolean isTransitionAllowed(Lead.LeadStatus fromNormalized, Lead.LeadStatus toNormalized) {
        if (fromNormalized == toNormalized) {
            return true;
        }
        WorkflowDefinition def = workflowDefinitionRepository.findByKey(DEFAULT_KEY)
                .orElseThrow(() -> new IllegalStateException("Workflow DEFAULT not seeded"));
        String from = fromNormalized.name();
        String to = toNormalized.name();
        for (WorkflowDefinition.Transition t : def.getTransitions()) {
            if (from.equals(t.getFromState()) && to.equals(t.getToState())) {
                return true;
            }
        }
        return false;
    }

    public List<Lead.LeadStatus> allowedNextStates(Lead lead) {
        Lead.LeadStatus cur = normalizeCurrentState(lead);
        return workflowDefinitionRepository.findByKey(DEFAULT_KEY)
                .map(def -> computeAllowedNextStates(cur, def))
                .orElseGet(List::of);
    }

    private List<Lead.LeadStatus> computeAllowedNextStates(Lead.LeadStatus cur, WorkflowDefinition def) {
        if (def.getTransitions() == null || def.getTransitions().isEmpty()) {
            return List.of();
        }
        Set<Lead.LeadStatus> out = new LinkedHashSet<>();
        for (WorkflowDefinition.Transition t : def.getTransitions()) {
            if (cur.name().equals(t.getFromState())) {
                try {
                    out.add(Lead.LeadStatus.valueOf(t.getToState()));
                } catch (IllegalArgumentException ignored) {
                    // skip unknown enum names in DB
                }
            }
        }
        return new ArrayList<>(out);
    }
}
