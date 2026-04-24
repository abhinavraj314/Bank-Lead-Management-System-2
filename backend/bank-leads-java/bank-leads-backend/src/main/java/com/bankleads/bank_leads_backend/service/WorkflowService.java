package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.model.WorkflowDefinition;
import com.bankleads.bank_leads_backend.repository.WorkflowDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    public static final String DEFAULT_KEY = "DEFAULT";
    public static final String TEAM_KEY_PREFIX = "TEAM_";

    private final WorkflowDefinitionRepository workflowDefinitionRepository;

    public String teamKey(String teamId) {
        return TEAM_KEY_PREFIX + teamId;
    }

    public Optional<WorkflowDefinition> findTeamWorkflow(String teamId) {
        if (teamId == null || teamId.isBlank()) {
            return Optional.empty();
        }
        return workflowDefinitionRepository.findByKey(teamKey(teamId.trim()));
    }

    public WorkflowDefinition getDefaultWorkflowOrThrow() {
        return workflowDefinitionRepository.findByKey(DEFAULT_KEY)
                .orElseThrow(() -> new IllegalStateException("Workflow DEFAULT not seeded"));
    }

    public WorkflowDefinition resolveWorkflowForLead(Lead lead) {
        if (lead != null && lead.getTeamId() != null && !lead.getTeamId().isBlank()) {
            Optional<WorkflowDefinition> team = findTeamWorkflow(lead.getTeamId());
            if (team.isPresent()) {
                return team.get();
            }
        }
        return getDefaultWorkflowOrThrow();
    }

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
        return isTransitionAllowed(fromNormalized, toNormalized, null);
    }

    public boolean isTransitionAllowed(Lead.LeadStatus fromNormalized, Lead.LeadStatus toNormalized, Lead leadContext) {
        if (fromNormalized == toNormalized) {
            return true;
        }
        WorkflowDefinition def = leadContext != null ? resolveWorkflowForLead(leadContext) : getDefaultWorkflowOrThrow();
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
        WorkflowDefinition def = resolveWorkflowForLead(lead);
        return computeAllowedNextStates(cur, def);
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
