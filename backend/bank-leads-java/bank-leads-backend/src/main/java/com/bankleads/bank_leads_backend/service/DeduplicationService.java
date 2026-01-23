package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.repository.LeadRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeduplicationService {
    
    private final LeadRepository leadRepository;
    private final MongoTemplate mongoTemplate;
    
    private DeduplicationConfig config = new DeduplicationConfig(true, true, true);
    
    public DeduplicationConfig getDeduplicationConfig() {
        return new DeduplicationConfig(config.isUseEmail(), config.isUsePhone(), config.isUseAadhar());
    }
    
    public DeduplicationConfig updateDeduplicationConfig(DeduplicationConfig newConfig) {
        if (newConfig.isUseEmail() != config.isUseEmail()) {
            config.setUseEmail(newConfig.isUseEmail());
        }
        if (newConfig.isUsePhone() != config.isUsePhone()) {
            config.setUsePhone(newConfig.isUsePhone());
        }
        if (newConfig.isUseAadhar() != config.isUseAadhar()) {
            config.setUseAadhar(newConfig.isUseAadhar());
        }
        return getDeduplicationConfig();
    }
    
    @Transactional
    public DeduplicationStats executeDeduplication(DeduplicationConfig overrideConfig) {
        DeduplicationConfig activeConfig = overrideConfig != null ? overrideConfig : config;
        
        long totalLeads = leadRepository.count();
        
        List<List<Lead>> duplicateGroups = findDuplicateGroups(activeConfig);
        
        List<MergeDetail> mergeDetails = new ArrayList<>();
        int mergedCount = 0;
        
        for (List<Lead> group : duplicateGroups) {
            MergeResult result = mergeLeads(group);
            mergeDetails.add(new MergeDetail(
                    result.getKeptLeadId(),
                    result.getMergedLeadIds(),
                    group.get(0).getEmail(),
                    group.get(0).getPhoneNumber(),
                    group.get(0).getAadharNumber()
            ));
            mergedCount += result.getMergedLeadIds().size();
        }
        
        long finalCount = leadRepository.count();
        
        return new DeduplicationStats(
                totalLeads,
                duplicateGroups.stream().mapToInt(g -> g.size() - 1).sum(),
                mergedCount,
                finalCount,
                mergeDetails
        );
    }
    
    private List<List<Lead>> findDuplicateGroups(DeduplicationConfig config) {
        List<List<Lead>> duplicateGroups = new ArrayList<>();
        Set<String> processed = new HashSet<>();
        
        List<Lead> allLeads = leadRepository.findAll();
        
        for (Lead lead : allLeads) {
            if (processed.contains(lead.getLeadId())) {
                continue;
            }
            
            Query query = new Query();
            List<Criteria> criteriaList = new ArrayList<>();
            
            if (config.isUseEmail() && lead.getEmail() != null) {
                criteriaList.add(Criteria.where("email").is(lead.getEmail()));
            }
            if (config.isUsePhone() && lead.getPhoneNumber() != null) {
                criteriaList.add(Criteria.where("phoneNumber").is(lead.getPhoneNumber()));
            }
            if (config.isUseAadhar() && lead.getAadharNumber() != null) {
                criteriaList.add(Criteria.where("aadharNumber").is(lead.getAadharNumber()));
            }
            
            if (criteriaList.isEmpty()) {
                continue;
            }
            
            query.addCriteria(new Criteria().orOperator(criteriaList.toArray(new Criteria[0])))
                    .addCriteria(Criteria.where("leadId").ne(lead.getLeadId()));
            
            List<Lead> duplicates = mongoTemplate.find(query, Lead.class);
            
            if (!duplicates.isEmpty()) {
                List<Lead> group = new ArrayList<>();
                group.add(lead);
                group.addAll(duplicates);
                
                group.sort(Comparator.comparing(Lead::getCreatedAt));
                duplicateGroups.add(group);
                
                group.forEach(l -> processed.add(l.getLeadId()));
            }
        }
        
        return duplicateGroups;
    }
    
    private MergeResult mergeLeads(List<Lead> group) {
        if (group.size() < 2) {
            throw new IllegalArgumentException("Group must have at least 2 leads to merge");
        }
        
        Lead keptLead = group.get(0); // Oldest lead
        List<Lead> toMerge = group.subList(1, group.size());
        
        for (Lead lead : toMerge) {
            // Fill missing fields
            if ((keptLead.getName() == null || keptLead.getName().isEmpty()) && lead.getName() != null) {
                keptLead.setName(lead.getName());
            }
            if ((keptLead.getPhoneNumber() == null || keptLead.getPhoneNumber().isEmpty()) && lead.getPhoneNumber() != null) {
                keptLead.setPhoneNumber(lead.getPhoneNumber());
            }
            if ((keptLead.getEmail() == null || keptLead.getEmail().isEmpty()) && lead.getEmail() != null) {
                keptLead.setEmail(lead.getEmail());
            }
            if ((keptLead.getAadharNumber() == null || keptLead.getAadharNumber().isEmpty()) && lead.getAadharNumber() != null) {
                keptLead.setAadharNumber(lead.getAadharNumber());
            }
            
            // Merge sources_seen
            if (lead.getSourcesSeen() != null) {
                for (String sourceId : lead.getSourcesSeen()) {
                    if (!keptLead.getSourcesSeen().contains(sourceId)) {
                        keptLead.getSourcesSeen().add(sourceId);
                    }
                }
            }
            
            // Merge products_seen
            if (lead.getProductsSeen() != null) {
                for (String productId : lead.getProductsSeen()) {
                    if (!keptLead.getProductsSeen().contains(productId)) {
                        keptLead.getProductsSeen().add(productId);
                    }
                }
            }
            
            // Update source_id and p_id if kept lead doesn't have them
            if ((keptLead.getSourceId() == null || keptLead.getSourceId().isEmpty()) && lead.getSourceId() != null) {
                keptLead.setSourceId(lead.getSourceId());
            }
            if ((keptLead.getPId() == null || keptLead.getPId().isEmpty()) && lead.getPId() != null) {
                keptLead.setPId(lead.getPId());
            }
            
            // Add to merged_from array
            Lead.MergeRecord mergeRecord = Lead.MergeRecord.builder()
                    .timestamp(LocalDateTime.now())
                    .sourceId(lead.getSourceId())
                    .pId(lead.getPId())
                    .data(null)
                    .build();
            keptLead.getMergedFrom().add(mergeRecord);
        }
        
        leadRepository.save(keptLead);
        
        List<String> mergedLeadIds = toMerge.stream()
                .map(Lead::getLeadId)
                .collect(Collectors.toList());
        
        leadRepository.deleteAll(toMerge);
        
        return new MergeResult(keptLead.getLeadId(), mergedLeadIds);
    }
    
    public DeduplicationStatsInfo getDeduplicationStats() {
        long totalLeads = leadRepository.count();
        
        // Simplified: count leads with duplicate identifiers
        long potentialDuplicates = 0; // Would need aggregation pipeline for accurate count
        
        return new DeduplicationStatsInfo(totalLeads, potentialDuplicates, getDeduplicationConfig());
    }
    
    @Data
    public static class DeduplicationConfig {
        private boolean useEmail;
        private boolean usePhone;
        private boolean useAadhar;
        
        public DeduplicationConfig(boolean useEmail, boolean usePhone, boolean useAadhar) {
            this.useEmail = useEmail;
            this.usePhone = usePhone;
            this.useAadhar = useAadhar;
        }
    }
    
    @Data
    public static class DeduplicationStats {
        private long totalLeads;
        private long duplicatesFound;
        private int mergedCount;
        private long finalCount;
        private List<MergeDetail> mergeDetails;
        
        public DeduplicationStats(long totalLeads, long duplicatesFound, int mergedCount, long finalCount, List<MergeDetail> mergeDetails) {
            this.totalLeads = totalLeads;
            this.duplicatesFound = duplicatesFound;
            this.mergedCount = mergedCount;
            this.finalCount = finalCount;
            this.mergeDetails = mergeDetails;
        }
    }
    
    @Data
    @lombok.AllArgsConstructor
    public static class MergeDetail {
        private String keptLeadId;
        private List<String> mergedLeadIds;
        private String email;
        private String phone;
        private String aadhar;
    }
    
    @Data
    @lombok.AllArgsConstructor
    public static class MergeResult {
        private String keptLeadId;
        private List<String> mergedLeadIds;
    }
    
    @Data
    @lombok.AllArgsConstructor
    public static class DeduplicationStatsInfo {
        private long totalLeads;
        private long potentialDuplicates;
        private DeduplicationConfig config;
    }
}
