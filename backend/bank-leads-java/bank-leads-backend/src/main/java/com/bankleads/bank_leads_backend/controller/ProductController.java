package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.request.CreateProductRequest;
import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.model.Product;
import com.bankleads.bank_leads_backend.model.ProductRankingProfile;
import com.bankleads.bank_leads_backend.repository.ProductRankingProfileRepository;
import com.bankleads.bank_leads_backend.repository.ProductRepository;
import com.bankleads.bank_leads_backend.repository.SourceRepository;
import com.bankleads.bank_leads_backend.repository.TeamRepository;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")  // ✅ ADDED - Allow CORS
@RequiredArgsConstructor
public class ProductController {
    
    private final ProductRepository productRepository;
    private final SourceRepository sourceRepository;
    private final ProductRankingProfileRepository productRankingProfileRepository;
    private final TeamRepository teamRepository;
    
    // ✅ NEW - Simple test endpoint
    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> test() {
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Product API is working!",
            "timestamp", LocalDateTime.now().toString()
        ));
    }
    
    // ✅ NEW - Get all products without pagination (for testing)
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<Product>>> getAllProducts() {
        try {
            List<Product> products = productRepository.findAll();
            ApiResponse<List<Product>> response = new ApiResponse<>();
            response.setData(products);
            response.setMessage("Products retrieved successfully");
            response.setSuccess(true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<List<Product>> errorResponse = new ApiResponse<>();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<Product>> createProduct(@Valid @RequestBody CreateProductRequest request) {
        // Validate team exists
        String teamId = request.getTeamId();
        if (teamId == null || teamId.trim().isEmpty()) {
            return ResponseUtil.error("Team ID is required", HttpStatus.BAD_REQUEST);
        }
        
        teamId = teamId.trim();
        if (!teamRepository.existsById(teamId)) {
            return ResponseUtil.error("Team not found: " + teamId, HttpStatus.BAD_REQUEST);
        }
        
        String providedPId = request.getPId();
        String pId = (providedPId == null || providedPId.trim().isEmpty())
                ? generateUniqueProductId()
                : providedPId.trim().toUpperCase();

        if (productRepository.existsByPId(pId)) {
            // If pId was provided explicitly, report conflict; otherwise regenerate once.
            if (providedPId != null && !providedPId.trim().isEmpty()) {
                return ResponseUtil.error("Product with p_id '" + pId + "' already exists",
                        HttpStatus.CONFLICT);
            }
            pId = generateUniqueProductId();
        }

        Product product = Product.builder()
                .pId(pId)
                .pName(request.getPName())
                .teamId(teamId)
                .deduplicationFields(request.getDeduplicationFields() != null ? request.getDeduplicationFields() : new java.util.ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        Product saved = productRepository.save(product);
        return ResponseUtil.success(saved, "Product created successfully",
                HttpStatus.CREATED);
    }

    private String generateUniqueProductId() {
        // Auto-generate sequential IDs like LOAN004, LOAN005, ...
        // We look for existing product IDs that match LOAN + 3 digits.
        final String prefix = "LOAN";
        final Pattern pattern = Pattern.compile("^" + prefix + "(\\d{3})$", Pattern.CASE_INSENSITIVE);

        int maxNumeric = 0;
        List<Product> allProducts = productRepository.findAll();
        for (Product p : allProducts) {
            if (p == null || p.getPId() == null) continue;
            String pId = p.getPId().toUpperCase();
            Matcher matcher = pattern.matcher(pId);
            if (matcher.matches()) {
                int num = Integer.parseInt(matcher.group(1));
                maxNumeric = Math.max(maxNumeric, num);
            }
        }

        // Start from max+1 and increment until we find an unused one.
        for (int i = 1; i <= 100; i++) {
            int candidateNum = maxNumeric + i;
            String candidate = prefix + String.format("%03d", candidateNum);
            if (!productRepository.existsByPId(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Could not generate a unique sequential product id");
    }
    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<Product>>> getProducts(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        try {
            // ✅ FIXED - Handle page starting from 1
            int pageIndex = Math.max(0, page - 1);
            int pageSize = Math.min(100, Math.max(1, limit));
            
            Pageable pageable = PageRequest.of(pageIndex, pageSize,
                    Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Product> products = productRepository.findAll(pageable);
            
            ApiResponse<Page<Product>> response = new ApiResponse<>();
            response.setData(products);
            response.setMessage("Products retrieved successfully");
            response.setSuccess(true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<Page<Product>> errorResponse = new ApiResponse<>();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Error retrieving products: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    @GetMapping("/{id}/ranking-profile")
    public ResponseEntity<ApiResponse<ProductRankingProfile>> getRankingProfile(@PathVariable String id) {
        String pId = id.toUpperCase();
        if (!productRepository.existsByPId(pId)) {
            return ResponseUtil.error("Product with p_id '" + id + "' not found", HttpStatus.NOT_FOUND);
        }
        ProductRankingProfile profile = productRankingProfileRepository.findByPId(pId)
                .orElse(ProductRankingProfile.builder()
                        .pId(pId)
                        .rules(new ArrayList<>())
                        .updatedAt(null)
                        .build());
        return ResponseUtil.success(profile);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/ranking-profile")
    public ResponseEntity<ApiResponse<ProductRankingProfile>> saveRankingProfile(
            @PathVariable String id,
            @RequestBody ProductRankingProfile body) {
        String pId = id.toUpperCase();
        if (!productRepository.existsByPId(pId)) {
            return ResponseUtil.error("Product with p_id '" + id + "' not found", HttpStatus.NOT_FOUND);
        }
        ProductRankingProfile existing = productRankingProfileRepository.findByPId(pId).orElse(null);
        ProductRankingProfile toSave;
        if (existing != null) {
            existing.setRules(body.getRules() != null ? body.getRules() : new ArrayList<>());
            existing.setUpdatedAt(LocalDateTime.now());
            toSave = existing;
        } else {
            toSave = ProductRankingProfile.builder()
                    .pId(pId)
                    .rules(body.getRules() != null ? body.getRules() : new ArrayList<>())
                    .updatedAt(LocalDateTime.now())
                    .build();
        }
        ProductRankingProfile saved = productRankingProfileRepository.save(toSave);
        return ResponseUtil.success(saved, "Ranking profile saved");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable String id) {
        return productRepository.findByPId(id.toUpperCase())
                .map(product -> ResponseUtil.success(product))
                .orElse(ResponseUtil.error("Product with p_id '" + id + "' not found",
                        HttpStatus.NOT_FOUND));
    }
    
    
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> updateProduct(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {
        return productRepository.findByPId(id.toUpperCase())
                .map(product -> {
                    if (updates.containsKey("p_name") && updates.get("p_name") != null) {
                        product.setPName((String) updates.get("p_name"));
                    }
                    if (updates.containsKey("deduplication_fields")) {
                        Object raw = updates.get("deduplication_fields");
                        if (raw instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<String> list = (List<String>) raw;
                            product.setDeduplicationFields(list);
                        }
                    }
                    product.setUpdatedAt(LocalDateTime.now());
                    Product saved = productRepository.save(product);
                    return ResponseUtil.success(saved, "Product updated successfully");
                })
                .orElse(ResponseUtil.error("Product with p_id '" + id + "' not found",
                        HttpStatus.NOT_FOUND));
    }
    
    
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteProduct(@PathVariable String id) {
        String pId = id.toUpperCase();
        
        if (sourceRepository.countByPId(pId) > 0) {
            return ResponseUtil.error("Cannot delete product: sources are associated with this product",
                    HttpStatus.CONFLICT);
        }
        
        return productRepository.findByPId(pId)
                .map(product -> {
                    productRepository.delete(product);
                    return ResponseUtil.success((Object) Map.of("message", "Product deleted successfully"));
                })
                .orElse(ResponseUtil.error("Product with p_id '" + id + "' not found",
                        HttpStatus.NOT_FOUND));
    }
}
