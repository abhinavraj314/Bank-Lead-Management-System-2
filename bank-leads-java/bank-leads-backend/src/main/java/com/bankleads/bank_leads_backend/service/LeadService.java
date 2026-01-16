package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeadService {
    
    private final LeadRepository leadRepository;
    
    public Optional<Lead> findByLeadId(String leadId) {
        return leadRepository.findByLeadId(leadId);
    }
    
    @Transactional
    public UpsertResult upsertLead(Map<String, String> normalized, UpsertContext ctx) {
        // Check for existing lead
        Optional<Lead> existingOpt = findExistingLead(normalized);
        
        if (existingOpt.isPresent()) {
            Lead existing = existingOpt.get();
            Lead merged = mergeLeadData(existing, normalized, ctx);
            Lead saved = leadRepository.save(merged);
            return new UpsertResult("merged", saved);
        }
        
        // Create new lead
        Lead newLead = Lead.builder()
                .leadId(UUID.randomUUID().toString())
                .name(normalized.get("name"))
                .phoneNumber(normalized.get("phone_number"))
                .email(normalized.get("email"))
                .aadharNumber(normalized.get("aadhar_number"))
                .sourceId(ctx.sourceId)
                .pId(ctx.pId)
                .createdAt(LocalDateTime.now())
                .mergedFrom(new ArrayList<>())
                .sourcesSeen(new ArrayList<>(Collections.singletonList(ctx.sourceId)))
                .productsSeen(new ArrayList<>(Collections.singletonList(ctx.pId)))
                .leadScore(null)
                .scoreReason(null)
                .build();
        
        Lead.MergeRecord initialMerge = Lead.MergeRecord.builder()
                .timestamp(LocalDateTime.now())
                .sourceId(ctx.sourceId)
                .pId(ctx.pId)
                .data(ctx.rawRow)
                .build();
        
        newLead.getMergedFrom().add(initialMerge);
        
        Lead saved = leadRepository.save(newLead);
        return new UpsertResult("inserted", saved);
    }
    
    private Optional<Lead> findExistingLead(Map<String, String> normalized) {
        String email = normalized.get("email");
        String phone = normalized.get("phone_number");
        String aadhar = normalized.get("aadhar_number");
        
        // Try email first
        if (email != null && !email.isEmpty()) {
            List<Lead> leads = leadRepository.findByEmail(email);
            if (!leads.isEmpty()) {
                return Optional.of(leads.get(0));
            }
        }
        
        // Try phone
        if (phone != null && !phone.isEmpty()) {
            List<Lead> leads = leadRepository.findByPhoneNumber(phone);
            if (!leads.isEmpty()) {
                return Optional.of(leads.get(0));
            }
        }
        
        // Try aadhar
        if (aadhar != null && !aadhar.isEmpty()) {
            List<Lead> leads = leadRepository.findByAadharNumber(aadhar);
            if (!leads.isEmpty()) {
                return Optional.of(leads.get(0));
            }
        }
        
        return Optional.empty();
    }
    
    private Lead mergeLeadData(Lead existing, Map<String, String> incoming, UpsertContext ctx) {
        // Fill missing fields
        if ((existing.getName() == null || existing.getName().isEmpty()) && incoming.get("name") != null) {
            existing.setName(incoming.get("name"));
        }
        if ((existing.getPhoneNumber() == null || existing.getPhoneNumber().isEmpty()) && incoming.get("phone_number") != null) {
            existing.setPhoneNumber(incoming.get("phone_number"));
        }
        if ((existing.getEmail() == null || existing.getEmail().isEmpty()) && incoming.get("email") != null) {
            existing.setEmail(incoming.get("email"));
        }
        if ((existing.getAadharNumber() == null || existing.getAadharNumber().isEmpty()) && incoming.get("aadhar_number") != null) {
            existing.setAadharNumber(incoming.get("aadhar_number"));
        }
        
        // Track source and product history
        if (!existing.getSourcesSeen().contains(ctx.sourceId)) {
            existing.getSourcesSeen().add(ctx.sourceId);
        }
        if (!existing.getProductsSeen().contains(ctx.pId)) {
            existing.getProductsSeen().add(ctx.pId);
        }
        
        // Store raw row for audit trail
        Lead.MergeRecord mergeRecord = Lead.MergeRecord.builder()
                .timestamp(LocalDateTime.now())
                .sourceId(ctx.sourceId)
                .pId(ctx.pId)
                .data(ctx.rawRow)
                .build();
        
        existing.getMergedFrom().add(mergeRecord);
        
        return existing;
    }
    
    public static class UpsertResult {
        private final String action; // "inserted" or "merged"
        private final Lead lead;
        
        public UpsertResult(String action, Lead lead) {
            this.action = action;
            this.lead = lead;
        }
        
        public String getAction() { return action; }
        public Lead getLead() { return lead; }
    }
    
    public static class UpsertContext {
        private final String pId;
        private final String sourceId;
        private final Object rawRow;
        
        public UpsertContext(String pId, String sourceId, Object rawRow) {
            this.pId = pId;
            this.sourceId = sourceId;
            this.rawRow = rawRow;
        }
        
        public String getPId() { return pId; }
        public String getSourceId() { return sourceId; }
        public Object getRawRow() { return rawRow; }
    }
}
