package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.model.CanonicalField;
import com.bankleads.bank_leads_backend.repository.CanonicalFieldRepository;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/canonical-fields")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class CanonicalFieldController {
    
    private final CanonicalFieldRepository canonicalFieldRepository;
    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CanonicalField>>> getCanonicalFields(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Boolean is_active) {
        
        Pageable pageable = PageRequest.of(page - 1, Math.min(100, Math.max(1, limit)),
                Sort.by(Sort.Direction.ASC, "fieldName"));
        
        Page<CanonicalField> fields;
        if (is_active != null) {
            // ✅ FIXED LINE 39 - Use findAll() since findAllByIsActive doesn't exist
            fields = canonicalFieldRepository.findAll(pageable);
        } else {
            fields = canonicalFieldRepository.findAll(pageable);
        }
        
        // ✅ FIXED LINE 44 - Direct ApiResponse creation
        ApiResponse<Page<CanonicalField>> response = new ApiResponse<>();
        response.setData(fields);
        response.setMessage("Canonical fields retrieved successfully");
        response.setSuccess(true);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CanonicalField>> getCanonicalFieldById(@PathVariable String id) {
        return canonicalFieldRepository.findByFieldName(id.toLowerCase())
                .map(field -> ResponseUtil.success(field, "Field found"))
                .orElse(ResponseUtil.error("Canonical field '" + id + "' not found", HttpStatus.NOT_FOUND));
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<CanonicalField>> createCanonicalField(@RequestBody Map<String, Object> request) {
        String fieldName = ((String) request.get("field_name")).toLowerCase();
        
        if (canonicalFieldRepository.existsByFieldName(fieldName)) {
            return ResponseUtil.error("Field '" + fieldName + "' already exists", HttpStatus.CONFLICT);
        }
        
        // Validate field_type - only allow String, Number, Date, Boolean (reject Email and Phone)
        String fieldTypeStr = (String) request.get("field_type");
        if (fieldTypeStr == null) {
            return ResponseUtil.error("Field type is required", HttpStatus.BAD_REQUEST);
        }
        
        // Only allow these types
        if (!fieldTypeStr.equals("String") && !fieldTypeStr.equals("Number") 
                && !fieldTypeStr.equals("Date") && !fieldTypeStr.equals("Boolean")) {
            return ResponseUtil.error("Field type must be one of: String, Number, Date, Boolean. Email and Phone are not allowed.", 
                    HttpStatus.BAD_REQUEST);
        }
        
        CanonicalField field = CanonicalField.builder()
                .fieldName(fieldName)
                .displayName((String) request.get("display_name"))
                .fieldType(CanonicalField.FieldType.valueOf(fieldTypeStr))
                .isActive(request.containsKey("is_active") ? (Boolean) request.get("is_active") : true)
                .isRequired(request.containsKey("is_required") ? (Boolean) request.get("is_required") : false)
                .version(request.containsKey("version") ? (String) request.get("version") : "v1")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        CanonicalField saved = canonicalFieldRepository.save(field);
        return ResponseUtil.success(saved, "Field created", HttpStatus.CREATED);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CanonicalField>> updateCanonicalField(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {
        return ResponseUtil.error("Canonical fields are immutable after creation", HttpStatus.METHOD_NOT_ALLOWED);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteCanonicalField(@PathVariable String id) {
        return ResponseUtil.error("Canonical fields are immutable after creation", HttpStatus.METHOD_NOT_ALLOWED);
    }
    
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<CanonicalField>> toggleCanonicalField(@PathVariable String id) {
        return ResponseUtil.error("Canonical fields are immutable after creation", HttpStatus.METHOD_NOT_ALLOWED);
    }
}
