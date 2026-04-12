package com.bankleads.bank_leads_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "lead_events")
@CompoundIndex(name = "lead_event_lead_time", def = "{'leadId': 1, 'at': -1}")
public class LeadEvent {

    public enum EventType {
        LEAD_CREATED,
        LEAD_MERGED,
        STATE_CHANGED,
        ASSIGNMENT_CHANGED,
        SCORE_UPDATED,
        TEAM_ASSIGNED
    }

    @Id
    private String id;

    /** Business lead id ({@link Lead#getLeadId()}) */
    private String leadId;

    private EventType type;

    /** When the event occurred */
    private Instant at;

    /** User id who caused the event, or null for system */
    private String actorUserId;

    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();
}
