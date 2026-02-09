package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.request.CreateProductRequest;
import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.model.Product;
import com.bankleads.bank_leads_backend.repository.ProductRepository;
import com.bankleads.bank_leads_backend.repository.SourceRepository;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")  // ✅ ADDED - Allow CORS
@RequiredArgsConstructor
public class ProductController {
    
    private final ProductRepository productRepository;
    private final SourceRepository sourceRepository;
    
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
        if (productRepository.existsByPId(request.getPId().toUpperCase())) {
            return ResponseUtil.error("Product with p_id '" + request.getPId() + "' already exists",
                    HttpStatus.CONFLICT);
        }
        
        Product product = Product.builder()
                .pId(request.getPId().toUpperCase())
                .pName(request.getPName())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        Product saved = productRepository.save(product);
        return ResponseUtil.success(saved, "Product created successfully",
                HttpStatus.CREATED);
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
            @RequestBody Map<String, String> updates) {
        return productRepository.findByPId(id.toUpperCase())
                .map(product -> {
                    if (updates.containsKey("p_name")) {
                        product.setPName(updates.get("p_name"));
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
