package com.bankleads.bank_leads_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-product ranking configuration including ML feature selection and post-ML adjustment rules.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "product_ranking_profiles")
public class ProductRankingProfile {

    @Id
    private String id;

    @Indexed(unique = true)
    private String pId;

    /**
     * List of ML features to use for this product's ranking.
     * Selected from high-impact features:
     * income, credit_score, loan_amount, emp_salaried, emp_self_employed,
     * has_email, has_phone, days_since_created
     */
    @Builder.Default
    private List<String> canonicalFields = new ArrayList<>();

    @Builder.Default
    private List<RankingRule> rules = new ArrayList<>();

    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RankingRule {
        /**
         * Logical field: name, email, phoneNumber, pId, sourceId, income, creditScore,
         * loanAmount, employmentType, converted
         */
        private String field;
        /**
         * EQ, NEQ, GT, GTE, LT, LTE, IS_EMPTY, NOT_EMPTY, CONTAINS
         */
        private String operator;
        /** Compared as string for EQ/NEQ/CONTAINS; parsed as number for numeric ops when applicable */
        private String value;
        /** Added to base ML score (typically small, e.g. -0.05 .. 0.1) */
        private double weightDelta;
        /** Optional cap on positive contribution from this rule */
        private Double maxBoost;
    }
}
