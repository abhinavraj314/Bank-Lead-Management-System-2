package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.dto.response.LeadSourceReportResponse;
import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.model.Source;
import com.bankleads.bank_leads_backend.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeadSourceReportService {

    private final MongoTemplate mongoTemplate;
    private final SourceRepository sourceRepository;

    public LeadSourceReportResponse getSourceAnalytics(
            String sourceId,
            String productId,
            String from,
            String to,
            String bucket,
            String currentUserId,
            boolean isAdmin) {

        Criteria criteria = buildCriteria(sourceId, productId, from, to, currentUserId, isAdmin);

        Aggregation sourceAndStateAgg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.project("sourceId")
                        .andExpression("ifNull(state, status)").as("effectiveState"),
                Aggregation.group("sourceId", "effectiveState").count().as("count")
        );

        AggregationResults<Map> sourceAndStateResults = mongoTemplate.aggregate(
                sourceAndStateAgg,
                Lead.class,
                Map.class
        );

        Map<String, SourceAccumulator> sourceCounters = new HashMap<>();
        Map<String, Long> stateDistribution = new HashMap<>();
        long totalLeads = 0L;
        long convertedLeads = 0L;

        for (Map row : sourceAndStateResults.getMappedResults()) {
            Map id = (Map) row.get("_id");
            String sId = safeString(id != null ? id.get("sourceId") : null);
            String rawState = safeString(id != null ? id.get("effectiveState") : null);
            String normalizedState = normalizeState(rawState);
            long count = numberToLong(row.get("count"));

            if (sId == null || sId.isBlank()) {
                sId = "UNKNOWN";
            }

            SourceAccumulator sourceAccumulator = sourceCounters.computeIfAbsent(sId, key -> new SourceAccumulator());
            sourceAccumulator.total += count;

            if ("CONVERTED".equals(normalizedState)) {
                sourceAccumulator.converted += count;
                convertedLeads += count;
            } else if ("NOT_CONVERTED".equals(normalizedState)) {
                sourceAccumulator.notConverted += count;
            }

            stateDistribution.merge(normalizedState, count, Long::sum);
            totalLeads += count;
        }

        Set<String> sourceIds = sourceCounters.keySet().stream()
                .filter(id -> !"UNKNOWN".equals(id))
                .collect(Collectors.toSet());
        Map<String, String> sourceNames = sourceRepository.findBySIdIn(sourceIds).stream()
                .collect(Collectors.toMap(Source::getSId, Source::getSName, (a, b) -> a));

        List<LeadSourceReportResponse.SourceBreakdownItem> sourceBreakdown = sourceCounters.entrySet().stream()
                .map(entry -> {
                    String sId = entry.getKey();
                    SourceAccumulator counter = entry.getValue();
                    return LeadSourceReportResponse.SourceBreakdownItem.builder()
                            .sourceId(sId)
                            .sourceName(sourceNames.getOrDefault(sId, sId))
                            .totalLeads(counter.total)
                            .convertedLeads(counter.converted)
                            .notConvertedLeads(counter.notConverted)
                            .conversionRate(calculateRate(counter.converted, counter.total))
                            .build();
                })
                .sorted(Comparator.comparingLong(LeadSourceReportResponse.SourceBreakdownItem::getTotalLeads)
                        .reversed())
                .toList();

        List<LeadSourceReportResponse.StateDistributionItem> stateItems = stateDistribution.entrySet().stream()
                .map(entry -> LeadSourceReportResponse.StateDistributionItem.builder()
                        .state(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .sorted(Comparator.comparingLong(LeadSourceReportResponse.StateDistributionItem::getCount)
                        .reversed())
                .toList();

        LeadSourceReportResponse.Summary summary = LeadSourceReportResponse.Summary.builder()
                .totalLeads(totalLeads)
                .convertedLeads(convertedLeads)
                .notConvertedLeads(Math.max(0L, totalLeads - convertedLeads))
                .conversionRate(calculateRate(convertedLeads, totalLeads))
                .build();

        List<LeadSourceReportResponse.TrendItem> trend = buildTrend(criteria, bucket);

        return LeadSourceReportResponse.builder()
                .summary(summary)
                .sourceBreakdown(sourceBreakdown)
                .stateDistribution(stateItems)
                .trend(trend)
                .build();
    }

    private List<LeadSourceReportResponse.TrendItem> buildTrend(Criteria criteria, String bucket) {
        String periodFormat = resolvePeriodFormat(bucket);

        AggregationOperation safeDateProjection = context -> new Document("$project", new Document()
                .append("period", new Document("$dateToString", new Document()
                        .append("format", periodFormat)
                        .append("date", new Document("$convert", new Document()
                                .append("input", "$createdAt")
                                .append("to", "date")
                                .append("onError", null)
                                .append("onNull", null)))))
                .append("effectiveState", new Document("$ifNull", List.of("$state", "$status"))));

        Aggregation trendAgg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                safeDateProjection,
                Aggregation.match(Criteria.where("period").ne(null)),
                Aggregation.group("period", "effectiveState").count().as("count")
        );

        AggregationResults<Map> trendResults = mongoTemplate.aggregate(
                trendAgg,
                Lead.class,
                Map.class
        );

        Map<String, TrendAccumulator> periodMap = new LinkedHashMap<>();

        for (Map row : trendResults.getMappedResults()) {
            Map id = (Map) row.get("_id");
            String period = safeString(id != null ? id.get("period") : null);
            String rawState = safeString(id != null ? id.get("effectiveState") : null);
            String normalizedState = normalizeState(rawState);
            long count = numberToLong(row.get("count"));

            if (period == null || period.isBlank()) {
                continue;
            }

            TrendAccumulator accumulator = periodMap.computeIfAbsent(period, k -> new TrendAccumulator());
            accumulator.total += count;
            if ("CONVERTED".equals(normalizedState)) {
                accumulator.converted += count;
            }
        }

        return periodMap.entrySet().stream()
                .map(entry -> LeadSourceReportResponse.TrendItem.builder()
                        .period(entry.getKey())
                        .totalLeads(entry.getValue().total)
                        .convertedLeads(entry.getValue().converted)
                        .build())
                .sorted(Comparator.comparing(LeadSourceReportResponse.TrendItem::getPeriod))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private Criteria buildCriteria(
            String sourceId,
            String productId,
            String from,
            String to,
            String currentUserId,
            boolean isAdmin) {

        List<Criteria> criteriaList = new ArrayList<>();

        if (sourceId != null && !sourceId.trim().isEmpty() && !"all".equalsIgnoreCase(sourceId.trim())) {
            criteriaList.add(Criteria.where("sourceId").is(sourceId.trim().toUpperCase(Locale.ROOT)));
        }

        if (productId != null && !productId.trim().isEmpty() && !"all".equalsIgnoreCase(productId.trim())) {
            criteriaList.add(Criteria.where("pId").is(productId.trim().toUpperCase(Locale.ROOT)));
        }

        if (!isAdmin) {
            if (currentUserId != null && !currentUserId.isBlank()) {
                criteriaList.add(Criteria.where("assignedUserId").is(currentUserId));
            } else {
                criteriaList.add(Criteria.where("assignedUserId").is(null));
            }
        }

        LocalDateTime fromDate = parseFrom(from);
        LocalDateTime toDate = parseTo(to);
        if (fromDate != null || toDate != null) {
            Criteria dateCriteria = Criteria.where("createdAt");
            if (fromDate != null) {
                dateCriteria = dateCriteria.gte(fromDate);
            }
            if (toDate != null) {
                dateCriteria = dateCriteria.lte(toDate);
            }
            criteriaList.add(dateCriteria);
        }

        if (criteriaList.isEmpty()) {
            return new Criteria();
        }
        if (criteriaList.size() == 1) {
            return criteriaList.get(0);
        }
        return new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
    }

    private LocalDateTime parseFrom(String from) {
        if (from == null || from.trim().isEmpty()) {
            return null;
        }
        try {
            if (from.contains("T")) {
                return LocalDateTime.parse(from.trim());
            }
            return LocalDate.parse(from.trim()).atStartOfDay();
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime parseTo(String to) {
        if (to == null || to.trim().isEmpty()) {
            return null;
        }
        try {
            if (to.contains("T")) {
                return LocalDateTime.parse(to.trim());
            }
            return LocalDate.parse(to.trim()).atTime(23, 59, 59);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolvePeriodFormat(String bucket) {
        if (bucket == null) {
            return "%Y-%m-%d";
        }
        return switch (bucket.toLowerCase(Locale.ROOT)) {
            case "month" -> "%Y-%m";
            case "week" -> "%G-W%V";
            default -> "%Y-%m-%d";
        };
    }

    private String normalizeState(String rawState) {
        if (rawState == null || rawState.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = rawState.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CLOSED" -> "NOT_CONVERTED";
            case "IN_PROGRESS" -> "CONTACTED";
            case "QUALIFIED" -> "PROPOSAL_SENT";
            default -> normalized;
        };
    }

    private String safeString(Object value) {
        return Objects.toString(value, null);
    }

    private long numberToLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private double calculateRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0;
        }
        return Math.round(((double) numerator * 10000.0) / denominator) / 100.0;
    }

    private static class SourceAccumulator {
        long total;
        long converted;
        long notConverted;
    }

    private static class TrendAccumulator {
        long total;
        long converted;
    }
}
