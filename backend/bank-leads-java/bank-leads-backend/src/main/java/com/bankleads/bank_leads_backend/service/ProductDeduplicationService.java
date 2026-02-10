package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.model.Product;
import com.bankleads.bank_leads_backend.model.Source;
import com.bankleads.bank_leads_backend.repository.LeadRepository;
import com.bankleads.bank_leads_backend.repository.ProductRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deduplicates products by normalized product name (p_name).
 * For each group of products with the same name, keeps one (oldest by createdAt),
 * reassigns all Lead and Source references from duplicate p_ids to the kept p_id,
 * then deletes the duplicate products.
 */
@Service
@RequiredArgsConstructor
public class ProductDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(ProductDeduplicationService.class);

    private final ProductRepository productRepository;
    private final LeadRepository leadRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Normalizes product name for grouping (trim, lower case).
     */
    public static String normalizeProductName(String pName) {
        if (pName == null) return "";
        return pName.trim().toLowerCase();
    }

    /**
     * Finds groups of products that share the same normalized p_name.
     * Returns only groups with more than one product.
     */
    public List<List<Product>> findDuplicateProductGroups() {
        List<Product> all = productRepository.findAll();
        Map<String, List<Product>> byName = new HashMap<>();
        for (Product p : all) {
            String key = normalizeProductName(p.getPName());
            byName.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }
        return byName.values().stream()
                .filter(group -> group.size() > 1)
                .peek(group -> group.sort(Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))))
                .collect(Collectors.toList());
    }

    /**
     * Executes product deduplication: for each duplicate group, keep one product,
     * reassign Lead and Source references, delete duplicates.
     */
    @Transactional
    public ProductDeduplicationResult executeProductDeduplication() {
        long totalProductsBefore = productRepository.count();
        List<List<Product>> duplicateGroups = findDuplicateProductGroups();
        List<ProductMergeDetail> mergeDetails = new ArrayList<>();
        int productsRemoved = 0;

        for (List<Product> group : duplicateGroups) {
            Product kept = group.get(0);
            List<Product> toRemove = group.subList(1, group.size());
            List<String> duplicatePIds = toRemove.stream().map(Product::getPId).collect(Collectors.toList());
            String keptPId = kept.getPId();

            // Reassign leads: pId and productsSeen
            for (String dupPId : duplicatePIds) {
                Query leadQuery = new Query(Criteria.where("pId").is(dupPId));
                List<Lead> leads = mongoTemplate.find(leadQuery, Lead.class);
                for (Lead lead : leads) {
                    lead.setPId(keptPId);
                    if (lead.getProductsSeen() != null) {
                        lead.getProductsSeen().remove(dupPId);
                        if (!lead.getProductsSeen().contains(keptPId)) {
                            lead.getProductsSeen().add(keptPId);
                        }
                    }
                    leadRepository.save(lead);
                }
            }

            // Reassign sources (Source uses @Field("p_id") in MongoDB)
            for (String dupPId : duplicatePIds) {
                Query sourceQuery = new Query(Criteria.where("p_id").is(dupPId));
                Update sourceUpdate = new Update().set("p_id", keptPId);
                mongoTemplate.updateMulti(sourceQuery, sourceUpdate, Source.class);
            }

            // Delete duplicate products
            productRepository.deleteAll(toRemove);
            productsRemoved += toRemove.size();

            mergeDetails.add(new ProductMergeDetail(
                    keptPId,
                    kept.getPName(),
                    duplicatePIds,
                    duplicatePIds.size()
            ));
        }

        long totalProductsAfter = productRepository.count();
        log.info("Product deduplication completed: groups={}, productsRemoved={}, before={}, after={}",
                duplicateGroups.size(), productsRemoved, totalProductsBefore, totalProductsAfter);

        return new ProductDeduplicationResult(
                totalProductsBefore,
                duplicateGroups.size(),
                productsRemoved,
                totalProductsAfter,
                mergeDetails
        );
    }

    @Data
    @lombok.AllArgsConstructor
    public static class ProductDeduplicationResult {
        private long totalProductsBefore;
        private int duplicateGroupsFound;
        private int productsRemoved;
        private long totalProductsAfter;
        private List<ProductMergeDetail> mergeDetails;
    }

    @Data
    @lombok.AllArgsConstructor
    public static class ProductMergeDetail {
        private String keptPId;
        private String keptPName;
        private List<String> mergedPIds;
        private int mergedCount;
    }
}
