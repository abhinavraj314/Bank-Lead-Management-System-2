package com.bankleads.bank_leads_backend.config;

import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.model.WorkflowDefinition;
import com.bankleads.bank_leads_backend.repository.WorkflowDefinitionRepository;
import com.bankleads.bank_leads_backend.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds or migrates the global workflow (Jira-style pipeline).
 */
@Component
@RequiredArgsConstructor
public class WorkflowBootstrap implements ApplicationRunner {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;

    @Override
    public void run(ApplicationArguments args) {
        List<WorkflowDefinition.Transition> transitions = buildDefaultTransitions();
        workflowDefinitionRepository.findByKey(WorkflowService.DEFAULT_KEY)
                .ifPresentOrElse(
                        existing -> {
                            if (needsMigrationToAssignedPipeline(existing)) {
                                existing.setTransitions(transitions);
                                workflowDefinitionRepository.save(existing);
                            }
                        },
                        () -> workflowDefinitionRepository.save(WorkflowDefinition.builder()
                                .key(WorkflowService.DEFAULT_KEY)
                                .transitions(transitions)
                                .build()));
    }

    /**
     * Old graphs had NEW → IN_PROGRESS but not NEW → ASSIGNED.
     */
    private static boolean needsMigrationToAssignedPipeline(WorkflowDefinition existing) {
        if (existing.getTransitions() == null || existing.getTransitions().isEmpty()) {
            return true;
        }
        return existing.getTransitions().stream().noneMatch(t ->
                Lead.LeadStatus.NEW.name().equals(t.getFromState())
                        && Lead.LeadStatus.ASSIGNED.name().equals(t.getToState()));
    }

    private static List<WorkflowDefinition.Transition> buildDefaultTransitions() {
        String n = Lead.LeadStatus.NEW.name();
        String ass = Lead.LeadStatus.ASSIGNED.name();
        String cont = Lead.LeadStatus.CONTACTED.name();
        String ps = Lead.LeadStatus.PROPOSAL_SENT.name();
        String conv = Lead.LeadStatus.CONVERTED.name();
        String nconv = Lead.LeadStatus.NOT_CONVERTED.name();

        return List.of(
                t(n, ass),
                t(n, cont),
                t(n, nconv),
                t(ass, cont),
                t(ass, nconv),
                t(cont, ps),
                t(cont, nconv),
                t(ps, conv),
                t(ps, nconv),
                t(ps, cont)
        );
    }

    private static WorkflowDefinition.Transition t(String from, String to) {
        return WorkflowDefinition.Transition.builder().fromState(from).toState(to).build();
    }
}
