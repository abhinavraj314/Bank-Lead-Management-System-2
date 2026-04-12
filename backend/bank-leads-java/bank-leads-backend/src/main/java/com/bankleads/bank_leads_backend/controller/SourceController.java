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
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/sources")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class SourceController {
    
    private final SourceRepository sourceRepository;
    private final LeadRepository leadRepository;
    
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<Source>> createSource(@Valid @RequestBody CreateSourceRequest request) {
        String providedSId = request.getSId();
        String sId = (providedSId == null || providedSId.trim().isEmpty())
                ? generateUniqueSourceId()
                : providedSId.trim().toUpperCase();

        if (sourceRepository.existsBySourceId(sId)) {
            // If sId was provided explicitly, report conflict; otherwise regenerate once.
            if (providedSId != null && !providedSId.trim().isEmpty()) {
                return ResponseUtil.error("Source with s_id '" + sId + "' already exists",
                        HttpStatus.CONFLICT);
            }
            sId = generateUniqueSourceId();
        }

        // Validate columns: at least one column must be provided
        List<String> rawColumns = request.getColumns();
        if (rawColumns == null || rawColumns.isEmpty()) {
            return ResponseUtil.error("At least one column is required for the source", HttpStatus.BAD_REQUEST);
        }

        // Normalize columns: trim and drop empty values
        List<String> normalizedColumns = rawColumns.stream()
                .filter(c -> c != null && !c.trim().isEmpty())
                .map(String::trim)
                .toList();

        if (normalizedColumns.isEmpty()) {
            return ResponseUtil.error("At least one valid (non-empty) column is required for the source",
                    HttpStatus.BAD_REQUEST);
        }
        
        Source source = Source.builder()
                .sId(sId)
                .sName(request.getSName())
                .pId(request.getPId().toUpperCase())
                .columns(normalizedColumns)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        Source saved = sourceRepository.save(source);
        return ResponseUtil.success(saved, "Source created successfully",
                HttpStatus.CREATED);
    }

    private String generateUniqueSourceId() {
        // Auto-generate sequential IDs like SRC001, SRC002, ...
        // We look for existing source IDs that match SRC + 3 digits.
        final String prefix = "SRC";
        final Pattern pattern = Pattern.compile("^" + prefix + "(\\d{3})$", Pattern.CASE_INSENSITIVE);

        int maxNumeric = 0;
        List<Source> allSources = sourceRepository.findAll();
        for (Source s : allSources) {
            if (s == null || s.getSId() == null) continue;
            String sId = s.getSId().toUpperCase();
            Matcher matcher = pattern.matcher(sId);
            if (matcher.matches()) {
                int num = Integer.parseInt(matcher.group(1));
                maxNumeric = Math.max(maxNumeric, num);
            }
        }

        // Start from max+1 and increment until we find an unused one.
        for (int i = 1; i <= 100; i++) {
            int candidateNum = maxNumeric + i;
            String candidate = prefix + String.format("%03d", candidateNum);
            if (!sourceRepository.existsBySourceId(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException("Could not generate a unique sequential source id");
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
    
    
    @PreAuthorize("hasRole('ADMIN')")
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
    
    
    @PreAuthorize("hasRole('ADMIN')")
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
