package com.bankleads.bank_leads_backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadSourceReportResponse {
    private Summary summary;
    private List<SourceBreakdownItem> sourceBreakdown;
    private List<StateDistributionItem> stateDistribution;
    private List<TrendItem> trend;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private long totalLeads;
        private long convertedLeads;
        private long notConvertedLeads;
        private double conversionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceBreakdownItem {
        private String sourceId;
        private String sourceName;
        private long totalLeads;
        private long convertedLeads;
        private long notConvertedLeads;
        private double conversionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StateDistributionItem {
        private String state;
        private long count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendItem {
        private String period;
        private long totalLeads;
        private long convertedLeads;
    }
}
