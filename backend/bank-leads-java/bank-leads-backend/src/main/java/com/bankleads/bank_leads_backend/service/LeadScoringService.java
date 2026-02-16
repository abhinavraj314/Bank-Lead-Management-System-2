package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LeadScoringService {
    
    private final LeadRepository leadRepository;
    
    @Transactional
    public ScoringResult scoreLead(Lead lead) {
        ScoringResult result = calculateLeadScore(lead);
        
        lead.setLeadScore(result.score);
        lead.setScoreReason(result.reason);
        
        leadRepository.save(lead);
        
        return result;
    }
    
    public ScoringResult calculateLeadScore(Lead lead) {
        double score = 0.0;
        Map<String, ScoringFactor> breakdown = new HashMap<>();
        
        breakdown.put("hasEmail", new ScoringFactor(30, false));
        breakdown.put("hasPhone", new ScoringFactor(30, false));
        breakdown.put("hasAadhar", new ScoringFactor(20, false));
        breakdown.put("hasName", new ScoringFactor(10, false));
        breakdown.put("multipleSources", new ScoringFactor(10, false));
        breakdown.put("multipleProducts", new ScoringFactor(10, false));
        
        // Has email
        if (lead.getEmail() != null && !lead.getEmail().trim().isEmpty()) {
            score += 30;
            breakdown.get("hasEmail").setApplied(true);
        }
        
        // Has phone
        if (lead.getPhoneNumber() != null && !lead.getPhoneNumber().trim().isEmpty()) {
            score += 30;
            breakdown.get("hasPhone").setApplied(true);
        }
        
        // Has aadhar
        if (lead.getAadharNumber() != null && !lead.getAadharNumber().trim().isEmpty()) {
            score += 20;
            breakdown.get("hasAadhar").setApplied(true);
        }
        
        // Has name
        if (lead.getName() != null && !lead.getName().trim().isEmpty()) {
            score += 10;
            breakdown.get("hasName").setApplied(true);
        }
        
        // Multiple sources
        if (lead.getSourcesSeen() != null && lead.getSourcesSeen().size() > 1) {
            score += 10;
            breakdown.get("multipleSources").setApplied(true);
        }
        
        // Multiple products
        if (lead.getProductsSeen() != null && lead.getProductsSeen().size() > 1) {
            score += 10;
            breakdown.get("multipleProducts").setApplied(true);
        }
        
        // Convert to probability in [0, 1] for ranking.
        double probability = Math.min(score, 100.0) / 100.0;
        
        // Generate reason
        StringBuilder reasonBuilder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, ScoringFactor> entry : breakdown.entrySet()) {
            if (entry.getValue().isApplied()) {
                if (!first) {
                    reasonBuilder.append(", ");
                }
                reasonBuilder.append(entry.getKey())
                        .append(" (+")
                        .append(entry.getValue().getPoints())
                        .append(")");
                first = false;
            }
        }
        
        String reason = reasonBuilder.length() > 0
                ? "Probability based on: " + reasonBuilder.toString()
                : "No scoring factors applied";
        
        return new ScoringResult(probability, reason, breakdown);
    }
    
    public static class ScoringResult {
        private final double score;
        private final String reason;
        private final Map<String, ScoringFactor> breakdown;
        
        public ScoringResult(double score, String reason, Map<String, ScoringFactor> breakdown) {
            this.score = score;
            this.reason = reason;
            this.breakdown = breakdown;
        }
        
        public double getScore() { return score; }
        public String getReason() { return reason; }
        public Map<String, ScoringFactor> getBreakdown() { return breakdown; }
    }
    
    public static class ScoringFactor {
        private int points;
        private boolean applied;
        
        public ScoringFactor(int points, boolean applied) {
            this.points = points;
            this.applied = applied;
        }
        
        public int getPoints() { return points; }
        public boolean isApplied() { return applied; }
        public void setApplied(boolean applied) { this.applied = applied; }
    }
}
