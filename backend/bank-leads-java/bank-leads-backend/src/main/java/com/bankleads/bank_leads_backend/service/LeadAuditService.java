package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.dto.response.LeadEventDTO;
import com.bankleads.bank_leads_backend.model.LeadEvent;
import com.bankleads.bank_leads_backend.repository.LeadEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LeadAuditService {

    private final LeadEventRepository leadEventRepository;

    public void append(String leadId, LeadEvent.EventType type, String actorUserId, Map<String, Object> payload) {
        if (leadId == null || leadId.isBlank()) {
            return;
        }
        LeadEvent ev = LeadEvent.builder()
                .leadId(leadId)
                .type(type)
                .at(Instant.now())
                .actorUserId(actorUserId)
                .payload(payload != null ? payload : Map.of())
                .build();
        leadEventRepository.save(ev);
    }

    public Page<LeadEventDTO> listHistory(String leadId, int page, int size) {
        Page<LeadEvent> p = leadEventRepository.findByLeadIdOrderByAtDesc(
                leadId, PageRequest.of(Math.max(0, page), Math.min(500, Math.max(1, size))));
        return p.map(e -> LeadEventDTO.builder()
                .id(e.getId())
                .leadId(e.getLeadId())
                .type(e.getType())
                .at(e.getAt())
                .actorUserId(e.getActorUserId())
                .payload(e.getPayload())
                .build());
    }
}
