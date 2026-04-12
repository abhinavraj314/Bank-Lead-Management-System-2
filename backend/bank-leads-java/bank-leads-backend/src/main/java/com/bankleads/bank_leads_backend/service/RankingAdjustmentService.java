package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.model.ProductRankingProfile;
import com.bankleads.bank_leads_backend.repository.ProductRankingProfileRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RankingAdjustmentService {

    private final ProductRankingProfileRepository profileRepository;

    public Result apply(Lead lead, double baseScore, String baseReason) {
        String pId = lead.getPId();
        if (pId == null || pId.isBlank()) {
            return Result.builder()
                    .finalScore(clamp01(baseScore))
                    .reason(baseReason)
                    .breakdown(Map.of("baseScore", baseScore, "rulesApplied", List.of()))
                    .build();
        }
        Optional<ProductRankingProfile> opt = profileRepository.findByPId(pId.toUpperCase());
        if (opt.isEmpty() || opt.get().getRules() == null || opt.get().getRules().isEmpty()) {
            return Result.builder()
                    .finalScore(clamp01(baseScore))
                    .reason(baseReason)
                    .breakdown(Map.of("baseScore", baseScore, "rulesApplied", List.of()))
                    .build();
        }

        double score = baseScore;
        List<Map<String, Object>> applied = new ArrayList<>();
        for (ProductRankingProfile.RankingRule rule : opt.get().getRules()) {
            if (!matches(lead, rule)) {
                continue;
            }
            double delta = rule.getWeightDelta();
            if (rule.getMaxBoost() != null && delta > 0) {
                delta = Math.min(delta, rule.getMaxBoost());
            }
            double before = score;
            score = clamp01(score + delta);
            applied.add(Map.of(
                    "field", rule.getField(),
                    "operator", rule.getOperator(),
                    "value", rule.getValue() != null ? rule.getValue() : "",
                    "delta", delta,
                    "before", before,
                    "after", score
            ));
        }

        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("baseScore", baseScore);
        breakdown.put("finalScore", score);
        breakdown.put("rulesApplied", applied);

        String reason = baseReason + (applied.isEmpty() ? "" : " + ranking rules (" + applied.size() + ")");
        return Result.builder()
                .finalScore(score)
                .reason(reason)
                .breakdown(breakdown)
                .build();
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private boolean matches(Lead lead, ProductRankingProfile.RankingRule rule) {
        String field = rule.getField() == null ? "" : rule.getField().trim();
        String op = rule.getOperator() == null ? "" : rule.getOperator().trim().toUpperCase(Locale.ROOT);
        String val = rule.getValue();

        String sVal = stringField(lead, field);
        Double nVal = numericField(lead, field);
        Boolean boolVal = booleanField(lead, field);

        return switch (op) {
            case "IS_EMPTY" -> isEmpty(sVal) && nVal == null && boolVal == null;
            case "NOT_EMPTY" -> !isEmpty(sVal) || nVal != null || boolVal != null;
            case "EQ" -> {
                if (val == null) {
                    yield false;
                }
                if ("converted".equalsIgnoreCase(field) && boolVal != null) {
                    yield boolVal == Boolean.parseBoolean(val.trim());
                }
                yield sVal != null && sVal.equalsIgnoreCase(val.trim());
            }
            case "NEQ" -> val != null && (sVal == null || !sVal.equalsIgnoreCase(val.trim()));
            case "CONTAINS" -> val != null && sVal != null && sVal.toLowerCase(Locale.ROOT).contains(val.toLowerCase(Locale.ROOT).trim());
            case "GT" -> compareNum(nVal, val) > 0;
            case "GTE" -> compareNum(nVal, val) >= 0;
            case "LT" -> compareNum(nVal, val) < 0;
            case "LTE" -> compareNum(nVal, val) <= 0;
            default -> false;
        };
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isBlank();
    }

    private int compareNum(Double actual, String expected) {
        if (actual == null || expected == null || expected.isBlank()) {
            return Integer.MIN_VALUE;
        }
        try {
            double e = Double.parseDouble(expected.trim());
            return Double.compare(actual, e);
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private String stringField(Lead lead, String field) {
        return switch (field.toLowerCase(Locale.ROOT)) {
            case "name" -> lead.getName();
            case "email" -> lead.getEmail();
            case "phonenumber", "phone" -> lead.getPhoneNumber();
            case "aadharnumber", "aadhar" -> lead.getAadharNumber();
            case "pid", "productid" -> lead.getPId();
            case "sourceid" -> lead.getSourceId();
            case "employmenttype" -> lead.getEmploymentType() != null ? lead.getEmploymentType().name() : null;
            default -> null;
        };
    }

    private Double numericField(Lead lead, String field) {
        return switch (field.toLowerCase(Locale.ROOT)) {
            case "income" -> lead.getIncome() != null ? lead.getIncome().doubleValue() : null;
            case "creditscore" -> lead.getCreditScore() != null ? lead.getCreditScore().doubleValue() : null;
            case "loanamount" -> lead.getLoanAmount() != null ? lead.getLoanAmount().doubleValue() : null;
            default -> null;
        };
    }

    private Boolean booleanField(Lead lead, String field) {
        if ("converted".equalsIgnoreCase(field)) {
            return lead.getConverted();
        }
        return null;
    }

    @Data
    @Builder
    public static class Result {
        private double finalScore;
        private String reason;
        private Map<String, Object> breakdown;
    }
}
