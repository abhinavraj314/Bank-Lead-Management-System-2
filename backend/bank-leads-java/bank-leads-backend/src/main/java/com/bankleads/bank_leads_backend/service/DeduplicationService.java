package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.model.Product;
import com.bankleads.bank_leads_backend.repository.LeadRepository;
import com.bankleads.bank_leads_backend.repository.ProductRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private static final Set<String> EMAIL_NAMES = Set.of("email");
    private static final Set<String> PHONE_NAMES = Set.of("phone_number", "phone");
    private static final Set<String> AADHAR_NAMES = Set.of("aadhar_number", "aadhar");
    
    private final LeadRepository leadRepository;
    private final ProductRepository productRepository;
    
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
    
    /**
     * Builds deduplication config from canonical field names (e.g. from Product.deduplicationFields).
     * Allowed names: "email", "phone_number", "aadhar_number" (or "phone", "aadhar").
     * If null or empty, returns config with all three enabled.
     */
    public DeduplicationConfig buildConfigFromCanonicalFieldNames(List<String> canonicalFieldNames) {
        if (canonicalFieldNames == null || canonicalFieldNames.isEmpty()) {
            return new DeduplicationConfig(true, true, true);
        }
        Set<String> normalized = canonicalFieldNames.stream()
                .map(s -> s == null ? "" : s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        boolean useEmail = normalized.stream().anyMatch(EMAIL_NAMES::contains);
        boolean usePhone = normalized.stream().anyMatch(PHONE_NAMES::contains);
        boolean useAadhar = normalized.stream().anyMatch(AADHAR_NAMES::contains);
        return new DeduplicationConfig(useEmail, usePhone, useAadhar);
    }
    
    @Transactional
    public DeduplicationStats executeDeduplication(DeduplicationConfig overrideConfig) {
        DeduplicationConfig activeConfig = overrideConfig != null ? overrideConfig : config;
        List<Lead> allLeads = leadRepository.findAll();
        return executeDeduplicationWithLeads(activeConfig, allLeads);
    }
    
    /**
     * Runs lead deduplication only for leads belonging to the given product (pId),
     * using that product's configured canonical deduplication fields.
     */
    @Transactional
    public DeduplicationStats executeDeduplicationForProduct(String pId) {
        String pIdUpper = pId == null ? null : pId.toUpperCase();
        Product product = productRepository.findByPId(pIdUpper)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + pId));
        DeduplicationConfig productConfig = buildConfigFromCanonicalFieldNames(product.getDeduplicationFields());
        List<Lead> productLeads = leadRepository.findByPId(pIdUpper);
        return executeDeduplicationWithLeads(productConfig, productLeads);
    }
    
    /**
     * Runs deduplication across all products: for each product, runs lead deduplication
     * for that product's leads using that product's deduplication fields.
     */
    @Transactional
    public Map<String, DeduplicationStats> executeDeduplicationForAllProducts() {
        Map<String, DeduplicationStats> byProduct = new LinkedHashMap<>();
        for (Product product : productRepository.findAll()) {
            String pId = product.getPId();
            try {
                DeduplicationStats stats = executeDeduplicationForProduct(pId);
                byProduct.put(pId, stats);
            } catch (Exception e) {
                // log and skip; or rethrow
                byProduct.put(pId, null); // or a stats with error message
            }
        }
        return byProduct;
    }
    
    private DeduplicationStats executeDeduplicationWithLeads(DeduplicationConfig activeConfig, List<Lead> candidateLeads) {
        long totalLeads = candidateLeads.size();
        List<List<Lead>> duplicateGroups = findDuplicateGroups(activeConfig, candidateLeads);
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
    
    private List<List<Lead>> findDuplicateGroups(DeduplicationConfig config, List<Lead> candidateLeads) {
        List<List<Lead>> duplicateGroups = new ArrayList<>();
        Set<String> processed = new HashSet<>();
        
        for (Lead lead : candidateLeads) {
            if (processed.contains(lead.getLeadId())) {
                continue;
            }
            
            boolean hasEmail = config.isUseEmail() && lead.getEmail() != null && !lead.getEmail().isEmpty();
            boolean hasPhone = config.isUsePhone() && lead.getPhoneNumber() != null && !lead.getPhoneNumber().isEmpty();
            boolean hasAadhar = config.isUseAadhar() && lead.getAadharNumber() != null && !lead.getAadharNumber().isEmpty();
            if (!hasEmail && !hasPhone && !hasAadhar) {
                continue;
            }
            
            List<Lead> duplicates = candidateLeads.stream()
                    .filter(other -> !other.getLeadId().equals(lead.getLeadId()))
                    .filter(other -> matchesForDeduplication(lead, other, config))
                    .collect(Collectors.toList());
            
            if (!duplicates.isEmpty()) {
                List<Lead> group = new ArrayList<>();
                group.add(lead);
                group.addAll(duplicates);
                group.sort(Comparator.comparing(Lead::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
                duplicateGroups.add(group);
                group.forEach(l -> processed.add(l.getLeadId()));
            }
        }
        
        return duplicateGroups;
    }
    
    private boolean matchesForDeduplication(Lead lead, Lead other, DeduplicationConfig config) {
        if (config.isUseEmail() && lead.getEmail() != null && lead.getEmail().equals(other.getEmail())) {
            return true;
        }
        if (config.isUsePhone() && lead.getPhoneNumber() != null && lead.getPhoneNumber().equals(other.getPhoneNumber())) {
            return true;
        }
        if (config.isUseAadhar() && lead.getAadharNumber() != null && lead.getAadharNumber().equals(other.getAadharNumber())) {
            return true;
        }
        return false;
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
