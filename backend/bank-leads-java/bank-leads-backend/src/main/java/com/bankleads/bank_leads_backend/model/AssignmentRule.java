package com.bankleads.bank_leads_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Routes new leads to a team (and round-robin user within the team). Lower {@link #priority} runs first.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "assignment_rules")
public class AssignmentRule {

    @Id
    private String id;

    @Indexed
    private int priority;

    /** Null or blank = match any product */
    private String productId;

    /** Null or blank = match any source */
    private String sourceId;

    private String teamId;
}
