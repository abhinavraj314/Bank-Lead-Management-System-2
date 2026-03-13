package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeadScoringService {

    private static final Logger log = LoggerFactory.getLogger(LeadScoringService.class);

    private final LeadRepository leadRepository;
    private final RestTemplate restTemplate;

    @Value("${app.ml.scoring-service-url:http://localhost:5001}")
    private String mlServiceUrl;

    public ScoringResult scoreLead(Lead lead) {
        try {
            return scoreLeadWithML(lead);
        } catch (Exception e) {
            log.warn("ML scoring failed, falling back to heuristic: {}", e.getMessage());
            return scoreLeadWithHeuristic(lead);
        }
    }

    public int batchScoreLeads(List<Lead> leads) {
        if (leads.isEmpty()) return 0;

        try {
            return batchScoreWithML(leads);
        } catch (Exception e) {
            log.warn("ML batch scoring failed, falling back to heuristic: {}", e.getMessage());
            return batchScoreWithHeuristic(leads);
        }
    }

    public boolean isMlServiceAvailable() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    mlServiceUrl + "/health", Map.class);
            Map<String, Object> body = response.getBody();
            return body != null && "ok".equals(body.get("status"));
        } catch (Exception e) {
            log.warn("ML service health check failed: url={}, error={}", mlServiceUrl + "/health", e.getMessage());
            return false;
        }
    }

    // ==================== ML Scoring ====================

    @SuppressWarnings("unchecked")
    private ScoringResult scoreLeadWithML(Lead lead) {
        Map<String, Object> requestBody = Map.of("leads", List.of(buildLeadDataMap(lead)));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                mlServiceUrl + "/predict", requestBody, Map.class);

        Map<String, Object> body = response.getBody();
        if (body == null || !body.containsKey("predictions")) {
            throw new RuntimeException("Invalid ML service response");
        }

        List<Map<String, Object>> predictions = (List<Map<String, Object>>) body.get("predictions");
        if (predictions.isEmpty()) {
            throw new RuntimeException("No predictions returned");
        }

        double probability = ((Number) predictions.get(0).get("probability")).doubleValue();

        lead.setLeadScore(probability);
        lead.setScoreReason("ML model prediction (LightGBM)");
        leadRepository.save(lead);

        return new ScoringResult(probability, "ML model prediction (LightGBM)", Map.of());
    }

    private static final int BATCH_CHUNK_SIZE = 500;

    @SuppressWarnings("unchecked")
    private int batchScoreWithML(List<Lead> leads) {
        Map<String, Double> scoreMap = new HashMap<>();
        for (int i = 0; i < leads.size(); i += BATCH_CHUNK_SIZE) {
            int end = Math.min(i + BATCH_CHUNK_SIZE, leads.size());
            List<Lead> chunk = leads.subList(i, end);
            List<Map<String, Object>> leadDataList = chunk.stream()
                    .map(this::buildLeadDataMap)
                    .collect(Collectors.toList());
            Map<String, Object> requestBody = Map.of("leads", leadDataList);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    mlServiceUrl + "/predict", requestBody, Map.class);

            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("predictions")) {
                throw new RuntimeException("Invalid ML service response");
            }

            List<Map<String, Object>> predictions = (List<Map<String, Object>>) body.get("predictions");
            for (Map<String, Object> pred : predictions) {
                String leadId = (String) pred.get("leadId");
                double prob = ((Number) pred.get("probability")).doubleValue();
                if (leadId != null) {
                    scoreMap.put(leadId, prob);
                }
            }
        }

        int count = 0;
        for (Lead lead : leads) {
            Double score = scoreMap.get(lead.getLeadId());
            if (score != null) {
                lead.setLeadScore(score);
                lead.setScoreReason("ML model prediction (LightGBM)");
                count++;
            }
        }

        leadRepository.saveAll(leads);
        return count;
    }

    private Map<String, Object> buildLeadDataMap(Lead lead) {
        Map<String, Object> data = new HashMap<>();
        data.put("leadId", lead.getLeadId());
        data.put("email", lead.getEmail());
        data.put("phoneNumber", lead.getPhoneNumber());
        data.put("aadharNumber", lead.getAadharNumber());
        data.put("name", lead.getName());
        data.put("sourcesSeen", lead.getSourcesSeen());
        data.put("productsSeen", lead.getProductsSeen());
        data.put("createdAt", lead.getCreatedAt() != null ? lead.getCreatedAt().toString() : null);
        data.put("pId", lead.getPId());
        data.put("income", lead.getIncome());
        data.put("creditScore", lead.getCreditScore());
        data.put("employmentType", lead.getEmploymentType() != null ? lead.getEmploymentType().name() : null);
        data.put("loanAmount", lead.getLoanAmount());
        return data;
    }

    // ==================== Heuristic Fallback ====================

    private ScoringResult scoreLeadWithHeuristic(Lead lead) {
        ScoringResult result = calculateHeuristicScore(lead);
        lead.setLeadScore(result.score);
        lead.setScoreReason(result.reason + " [heuristic fallback]");
        leadRepository.save(lead);
        return result;
    }

    private int batchScoreWithHeuristic(List<Lead> leads) {
        int count = 0;
        for (Lead lead : leads) {
            ScoringResult result = calculateHeuristicScore(lead);
            lead.setLeadScore(result.score);
            lead.setScoreReason(result.reason + " [heuristic fallback]");
            count++;
        }
        leadRepository.saveAll(leads);
        return count;
    }

    private ScoringResult calculateHeuristicScore(Lead lead) {
        double score = 0.0;
        Map<String, ScoringFactor> breakdown = new LinkedHashMap<>();

        breakdown.put("hasEmail", new ScoringFactor(20, false));
        breakdown.put("hasPhone", new ScoringFactor(20, false));
        breakdown.put("hasAadhar", new ScoringFactor(10, false));
        breakdown.put("hasName", new ScoringFactor(5, false));
        breakdown.put("multipleSources", new ScoringFactor(5, false));
        breakdown.put("multipleProducts", new ScoringFactor(5, false));
        breakdown.put("hasIncome", new ScoringFactor(10, false));
        breakdown.put("hasCreditScore", new ScoringFactor(10, false));
        breakdown.put("hasEmploymentType", new ScoringFactor(5, false));
        breakdown.put("hasLoanAmount", new ScoringFactor(10, false));

        if (lead.getEmail() != null && !lead.getEmail().trim().isEmpty()) {
            score += 20;
            breakdown.get("hasEmail").setApplied(true);
        }
        if (lead.getPhoneNumber() != null && !lead.getPhoneNumber().trim().isEmpty()) {
            score += 20;
            breakdown.get("hasPhone").setApplied(true);
        }
        if (lead.getAadharNumber() != null && !lead.getAadharNumber().trim().isEmpty()) {
            score += 10;
            breakdown.get("hasAadhar").setApplied(true);
        }
        if (lead.getName() != null && !lead.getName().trim().isEmpty()) {
            score += 5;
            breakdown.get("hasName").setApplied(true);
        }
        if (lead.getSourcesSeen() != null && lead.getSourcesSeen().size() > 1) {
            score += 5;
            breakdown.get("multipleSources").setApplied(true);
        }
        if (lead.getProductsSeen() != null && lead.getProductsSeen().size() > 1) {
            score += 5;
            breakdown.get("multipleProducts").setApplied(true);
        }
        if (lead.getIncome() != null && lead.getIncome() > 0) {
            score += 10;
            breakdown.get("hasIncome").setApplied(true);
        }
        if (lead.getCreditScore() != null && lead.getCreditScore() > 0) {
            score += 10;
            breakdown.get("hasCreditScore").setApplied(true);
        }
        if (lead.getEmploymentType() != null) {
            score += 5;
            breakdown.get("hasEmploymentType").setApplied(true);
        }
        if (lead.getLoanAmount() != null && lead.getLoanAmount() > 0) {
            score += 10;
            breakdown.get("hasLoanAmount").setApplied(true);
        }

        double probability = score / 100.0;
        String reason = "Heuristic score: " + (int) score + "/100";

        Map<String, Object> breakdownMap = new LinkedHashMap<>();
        breakdown.forEach((key, factor) -> breakdownMap.put(key,
                Map.of("weight", factor.getWeight(), "applied", factor.isApplied())));

        return new ScoringResult(probability, reason, breakdownMap);
    }

    // ==================== Inner Classes ====================

    public static class ScoringResult {
        private final double score;
        private final String reason;
        private final Map<String, Object> breakdown;

        public ScoringResult(double score, String reason, Map<String, Object> breakdown) {
            this.score = score;
            this.reason = reason;
            this.breakdown = breakdown;
        }

        public double getScore() { return score; }
        public String getReason() { return reason; }
        public Map<String, Object> getBreakdown() { return breakdown; }
    }

    private static class ScoringFactor {
        private final int weight;
        private boolean applied;

        public ScoringFactor(int weight, boolean applied) {
            this.weight = weight;
            this.applied = applied;
        }

        public int getWeight() { return weight; }
        public boolean isApplied() { return applied; }
        public void setApplied(boolean applied) { this.applied = applied; }
    }
}
