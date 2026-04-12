package com.bankleads.bank_leads_backend.dto.response;

import com.bankleads.bank_leads_backend.model.LeadEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadEventDTO {
    private String id;
    private String leadId;
    private LeadEvent.EventType type;
    private Instant at;
    private String actorUserId;
    private Map<String, Object> payload;
}
