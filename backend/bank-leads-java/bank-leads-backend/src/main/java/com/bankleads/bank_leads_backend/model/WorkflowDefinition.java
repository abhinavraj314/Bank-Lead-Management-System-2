package com.bankleads.bank_leads_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Configurable lead workflow: allowed state transitions (global DEFAULT key for now).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "workflow_definitions")
public class WorkflowDefinition {

    @Id
    private String id;

    @Indexed(unique = true)
    private String key;

    @Builder.Default
    private List<Transition> transitions = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Transition {
        /** {@link Lead.LeadStatus} name */
        private String fromState;
        /** {@link Lead.LeadStatus} name */
        private String toState;
    }
}
