package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.request.CreateSourceRequest;
import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.model.Source;
import com.bankleads.bank_leads_backend.repository.LeadRepository;
import com.bankleads.bank_leads_backend.repository.SourceRepository;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/sources")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SourceController {
    
    private final SourceRepository sourceRepository;
    private final LeadRepository leadRepository;
    
    @PostMapping
    public ResponseEntity<ApiResponse<Source>> createSource(@Valid @RequestBody CreateSourceRequest request) {
        // ✅ FIXED - Use getSId() and getSName()
        if (sourceRepository.existsBySourceId(request.getSId())) {
            return ResponseUtil.error("Source with s_id '" + request.getSId() + "' already exists",
                    HttpStatus.CONFLICT);
        }
        
        Source source = Source.builder()
                .sId(request.getSId().toUpperCase())
                .sName(request.getSName())
                .pId(request.getPId().toUpperCase())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        Source saved = sourceRepository.save(source);
        return ResponseUtil.success(saved, "Source created successfully",
                HttpStatus.CREATED);
    }
    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<Source>>> getSources(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        try {
            int pageIndex = Math.max(0, page - 1);
            int pageSize = Math.min(100, Math.max(1, limit));
            
            Pageable pageable = PageRequest.of(pageIndex, pageSize,
                    Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Source> sources = sourceRepository.findAll(pageable);
            
            ApiResponse<Page<Source>> response = new ApiResponse<>();
            response.setData(sources);
            response.setMessage("Sources retrieved successfully");
            response.setSuccess(true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<Page<Source>> errorResponse = new ApiResponse<>();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("Error retrieving sources: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Source>> getSourceById(@PathVariable String id) {
        return sourceRepository.findBySourceId(id.toUpperCase())
                .map(source -> ResponseUtil.success(source))
                .orElse(ResponseUtil.error("Source with s_id '" + id + "' not found",
                        HttpStatus.NOT_FOUND));
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Source>> updateSource(
            @PathVariable String id,
            @RequestBody Map<String, String> updates) {
        return sourceRepository.findBySourceId(id.toUpperCase())
                .map(source -> {
                    // ✅ FIXED - Use setSName()
                    if (updates.containsKey("s_name")) {
                        source.setSName(updates.get("s_name"));
                    }
                    source.setUpdatedAt(LocalDateTime.now());
                    Source saved = sourceRepository.save(source);
                    return ResponseUtil.success(saved, "Source updated successfully");
                })
                .orElse(ResponseUtil.error("Source with s_id '" + id + "' not found",
                        HttpStatus.NOT_FOUND));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteSource(@PathVariable String id) {
        String sId = id.toUpperCase();
        
        if (leadRepository.countBySourceId(sId) > 0) {
            return ResponseUtil.error("Cannot delete source: leads are associated with this source",
                    HttpStatus.CONFLICT);
        }
        
        return sourceRepository.findBySourceId(sId)
                .map(source -> {
                    sourceRepository.delete(source);
                    return ResponseUtil.success((Object) Map.of("message", "Source deleted successfully"));
                })
                .orElse(ResponseUtil.error("Source with s_id '" + id + "' not found",
                        HttpStatus.NOT_FOUND));
    }
}
