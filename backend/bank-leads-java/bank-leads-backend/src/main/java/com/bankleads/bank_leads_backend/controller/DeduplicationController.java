package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.model.Product;
import com.bankleads.bank_leads_backend.repository.ProductRepository;
import com.bankleads.bank_leads_backend.service.DeduplicationService;
import com.bankleads.bank_leads_backend.service.ProductDeduplicationService;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/deduplication")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class DeduplicationController {
    
    private final DeduplicationService deduplicationService;
    private final ProductDeduplicationService productDeduplicationService;
    private final ProductRepository productRepository;
    
    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<DeduplicationService.DeduplicationConfig>> getDeduplicationRules() {
        return ResponseUtil.success(deduplicationService.getDeduplicationConfig());
    }
    
    
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/rules")
    public ResponseEntity<ApiResponse<DeduplicationService.DeduplicationConfig>> updateDeduplicationRules(
            @RequestBody Map<String, Boolean> request) {
        
        DeduplicationService.DeduplicationConfig config = new DeduplicationService.DeduplicationConfig(
                request.getOrDefault("useEmail", true),
                request.getOrDefault("usePhone", true),
                request.getOrDefault("useAadhar", true)
        );
        
        DeduplicationService.DeduplicationConfig updated = deduplicationService.updateDeduplicationConfig(config);
        return ResponseUtil.success(updated, "Deduplication rules updated successfully");
    }
    
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/execute")
    public ResponseEntity<ApiResponse<DeduplicationService.DeduplicationStats>> executeDeduplication(
            @RequestBody(required = false) Map<String, Boolean> config) {
        
        DeduplicationService.DeduplicationConfig overrideConfig = null;
        if (config != null && !config.isEmpty()) {
            overrideConfig = new DeduplicationService.DeduplicationConfig(
                    config.getOrDefault("useEmail", true),
                    config.getOrDefault("usePhone", true),
                    config.getOrDefault("useAadhar", true)
            );
        }
        
        DeduplicationService.DeduplicationStats stats = deduplicationService.executeDeduplication(overrideConfig);
        return ResponseUtil.success(stats, "Deduplication completed successfully");
    }
    
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<DeduplicationService.DeduplicationStatsInfo>> getDeduplicationStats() {
        return ResponseUtil.success(deduplicationService.getDeduplicationStats());
    }

    // ---------- Product deduplication (by normalized p_name) ----------

    @GetMapping("/products/preview")
    public ResponseEntity<ApiResponse<List<List<Map<String, String>>>>> previewProductDuplicates() {
        List<List<Product>> groups = productDeduplicationService.findDuplicateProductGroups();
        List<List<Map<String, String>>> preview = groups.stream()
                .map(group -> group.stream()
                        .map(p -> Map.<String, String>of(
                                "id", p.getId() != null ? p.getId() : "",
                                "pId", p.getPId() != null ? p.getPId() : "",
                                "pName", p.getPName() != null ? p.getPName() : ""
                        ))
                        .collect(Collectors.toList()))
                .collect(Collectors.toList());
        return ResponseUtil.success(preview, "Product duplicate groups (by normalized name)");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/products/execute")
    public ResponseEntity<ApiResponse<ProductDeduplicationService.ProductDeduplicationResult>> executeProductDeduplication() {
        ProductDeduplicationService.ProductDeduplicationResult result = productDeduplicationService.executeProductDeduplication();
        return ResponseUtil.success(result, "Product deduplication completed successfully");
    }

    // ---------- Per-product lead deduplication (canonical fields chosen per product) ----------

    /** Get deduplication config for a product (canonical fields used for lead deduplication). */
    @GetMapping("/products/{pId}/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProductDeduplicationConfig(@PathVariable String pId) {
        return productRepository.findByPId(pId.toUpperCase())
                .map(product -> {
                    DeduplicationService.DeduplicationConfig config = deduplicationService.buildConfigFromCanonicalFieldNames(product.getDeduplicationFields());
                    Map<String, Object> data = new HashMap<>();
                    data.put("pId", product.getPId());
                    data.put("pName", product.getPName());
                    data.put("deduplicationFields", product.getDeduplicationFields());
                    data.put("resolvedConfig", Map.of(
                            "useEmail", config.isUseEmail(),
                            "usePhone", config.isUsePhone(),
                            "useAadhar", config.isUseAadhar()
                    ));
                    return ResponseUtil.success(data, "Product deduplication config");
                })
                .orElse(ResponseUtil.error("Product not found: " + pId, HttpStatus.NOT_FOUND));
    }

    /** Update which canonical fields a product uses for lead deduplication. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/products/{pId}/config")
    public ResponseEntity<ApiResponse<Product>> updateProductDeduplicationConfig(
            @PathVariable String pId,
            @RequestBody Map<String, Object> body) {
        List<String> fields = null;
        if (body.get("deduplication_fields") instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> raw = (List<Object>) body.get("deduplication_fields");
            fields = raw.stream().map(o -> o == null ? "" : o.toString().trim()).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        }
        final List<String> finalFields = fields != null ? fields : List.of();
        return productRepository.findByPId(pId.toUpperCase())
                .map(product -> {
                    product.setDeduplicationFields(finalFields);
                    Product saved = productRepository.save(product);
                    return ResponseUtil.success(saved, "Product deduplication config updated");
                })
                .orElse(ResponseUtil.error("Product not found: " + pId, HttpStatus.NOT_FOUND));
    }

    /** Run lead deduplication for a single product using that product's chosen canonical fields. */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/execute/by-product")
    public ResponseEntity<ApiResponse<DeduplicationService.DeduplicationStats>> executeDeduplicationByProduct(
            @RequestParam String productId) {
        try {
            DeduplicationService.DeduplicationStats stats = deduplicationService.executeDeduplicationForProduct(productId);
            return ResponseUtil.success(stats, "Lead deduplication completed for product " + productId);
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /** Run lead deduplication for all products (each product uses its own canonical field config). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/execute/by-product/all")
    public ResponseEntity<ApiResponse<Map<String, DeduplicationService.DeduplicationStats>>> executeDeduplicationForAllProducts() {
        Map<String, DeduplicationService.DeduplicationStats> results = deduplicationService.executeDeduplicationForAllProducts();
        return ResponseUtil.success(results, "Lead deduplication completed for all products");
    }
}
