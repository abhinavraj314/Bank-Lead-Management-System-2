package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.request.CreateLeadRequest;
import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.dto.response.LeadDTO;
import com.bankleads.bank_leads_backend.model.CanonicalField;
import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.model.Product;
import com.bankleads.bank_leads_backend.model.Source;
import com.bankleads.bank_leads_backend.repository.CanonicalFieldRepository;
import com.bankleads.bank_leads_backend.repository.LeadRepository;
import com.bankleads.bank_leads_backend.repository.ProductRepository;
import com.bankleads.bank_leads_backend.repository.SourceRepository;
import com.bankleads.bank_leads_backend.service.CanonicalFieldDeduplicationService;
import com.bankleads.bank_leads_backend.service.DeduplicationService;
import com.bankleads.bank_leads_backend.service.LeadScoringService;
import com.bankleads.bank_leads_backend.service.LeadService;
import com.bankleads.bank_leads_backend.util.CsvParserUtil;
import com.bankleads.bank_leads_backend.util.CsvValidationUtil;
import com.bankleads.bank_leads_backend.util.LeadNormalizationUtil;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leads")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class LeadController {

    private static final Logger log = LoggerFactory.getLogger(LeadController.class);
    
    private final LeadRepository leadRepository;
    private final ProductRepository productRepository;
    private final SourceRepository sourceRepository;
    private final CanonicalFieldRepository canonicalFieldRepository;
    private final LeadService leadService;
    private final LeadScoringService leadScoringService;
    private final CanonicalFieldDeduplicationService canonicalFieldDeduplicationService;
    private final DeduplicationService deduplicationService;
    private final MongoTemplate mongoTemplate;
    
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadLeads(
            @RequestParam("file") MultipartFile file,
            @RequestParam("p_id") String pId,
            @RequestParam("source_id") String sourceId) {
        
        final String pIdUpper = pId != null ? pId.toUpperCase() : null;
        final String sourceIdUpper = sourceId != null ? sourceId.toUpperCase() : null;
        log.info("Lead upload started: filename={}, sizeBytes={}, p_id={}, source_id={}",
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getSize() : null,
                pIdUpper, sourceIdUpper);

        if (file.isEmpty()) {
            log.warn("Lead upload rejected: empty file (p_id={}, source_id={})", pIdUpper, sourceIdUpper);
            return ResponseUtil.error("File is required", HttpStatus.BAD_REQUEST);
        }
        
        if (!productRepository.existsByPId(pIdUpper)) {
            log.warn("Lead upload rejected: product not found (p_id={})", pIdUpper);
            return ResponseUtil.error("Product '" + pId + "' not found",
                    HttpStatus.BAD_REQUEST);
        }
        
        if (!sourceRepository.existsBySourceId(sourceIdUpper)) {
            log.warn("Lead upload rejected: source not found (source_id={})", sourceIdUpper);
            return ResponseUtil.error("Source '" + sourceId + "' not found",
                    HttpStatus.BAD_REQUEST);
        }
        
        try {
            // Fetch related metadata for debugging (does not affect upload behavior)
            Optional<Source> sourceOpt = sourceRepository.findBySourceId(sourceIdUpper);
            List<String> sourceColumns = sourceOpt.map(Source::getColumns).orElse(null);
            log.info("Source columns for validation/debug (source_id={}): {}", sourceIdUpper, sourceColumns);

            // Log canonical fields (active + required) - currently not enforced for upload
            Page<CanonicalField> canonicalPage = canonicalFieldRepository.findAll(PageRequest.of(0, 1000));
            List<String> canonicalNames = canonicalPage.getContent().stream()
                    .map(CanonicalField::getFieldName)
                    .filter(Objects::nonNull)
                    .toList();
            List<String> requiredCanonicalNames = canonicalPage.getContent().stream()
                    .filter(f -> Boolean.TRUE.equals(f.getIsActive()) && Boolean.TRUE.equals(f.getIsRequired()))
                    .map(CanonicalField::getFieldName)
                    .filter(Objects::nonNull)
                    .toList();
            log.info("Canonical fields loaded: count={}, names={}", canonicalNames.size(), canonicalNames);
            log.info("Required canonical fields (active+required): {}", requiredCanonicalNames);

            // Keep row numbers for logging/debug; does not change core upload logic
            class RowCtx {
                final int rowNumber; // 1-based excluding header for CSV; Excel uses sheet row number
                final Map<String, String> raw;
                final Map<String, String> normalized;
                RowCtx(int rowNumber, Map<String, String> raw, Map<String, String> normalized) {
                    this.rowNumber = rowNumber;
                    this.raw = raw;
                    this.normalized = normalized;
                }
            }

            // Get active canonical fields for validation
            List<CanonicalField> activeCanonicalFields = canonicalPage.getContent().stream()
                    .filter(f -> f.getIsActive() != null && f.getIsActive())
                    .collect(Collectors.toList());
            
            List<RowCtx> rows = new ArrayList<>();
            String filename = file.getOriginalFilename().toLowerCase();
            
            if (filename.endsWith(".csv")) {
                // Parse CSV with canonical field validation (field count + datatype + required fields)
                CsvParserUtil.ParseResult parseResult = CsvParserUtil.parseCSV(file.getBytes(), activeCanonicalFields);

                // Log parse-stage failures (headers and mapping are logged inside CsvParserUtil)
                if (!parseResult.getInvalidRows().isEmpty()) {
                    log.warn("CSV parse produced invalid rows: invalidCount={}", parseResult.getInvalidRows().size());
                    for (CsvParserUtil.ParsedRow r : parseResult.getInvalidRows()) {
                        log.warn("Row {} failed during parsing/normalization: {}", r.getRow(), r.getErrors());
                    }
                }
                
                if (!parseResult.isSuccess() || parseResult.getValidRows().isEmpty()) {
                    log.warn("Lead upload rejected: CSV parse failed or no valid rows (invalidCount={})",
                            parseResult.getInvalidRows().size());
                    return ResponseUtil.error("Failed to parse CSV or no valid rows found",
                            HttpStatus.BAD_REQUEST,
                            parseResult.getInvalidRows().stream().map(r -> Map.of(
                                    "rowNumber", r.getRow(),
                                    "reason", String.join("; ", r.getErrors()),
                                    "rawInput", r.getData()
                            )).collect(Collectors.toList()));
                }
                
                for (CsvParserUtil.ParsedRow parsedRow : parseResult.getValidRows()) {
                    // parsedRow.getRow() is the CSV line number (header is row 1)
                    rows.add(new RowCtx(parsedRow.getRow() - 1, parsedRow.getData(), parsedRow.getData()));
                }
            } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                Workbook workbook = WorkbookFactory.create(file.getInputStream());
                Sheet sheet = workbook.getSheetAt(0);
                
                if (sheet == null || sheet.getPhysicalNumberOfRows() < 2) {
                    return ResponseUtil.error("Excel file has no data rows",
                            HttpStatus.BAD_REQUEST);
                }
                
                Row headerRow = sheet.getRow(0);
                List<String> headers = new ArrayList<>();
                for (Cell cell : headerRow) {
                    headers.add(getCellValueAsString(cell));
                }
                
                Map<String, String> headerMapping = LeadNormalizationUtil.normalizeHeaders(
                        headers.toArray(new String[0]));
                log.info("Excel headers parsed: {}", headers);
                log.info("Excel header mapping (original->canonical): {}", headerMapping);
                
                // Validate field count for Excel
                CsvValidationUtil.ValidationResult countValidation = CsvValidationUtil.validateFieldCount(headers, activeCanonicalFields);
                if (!countValidation.isValid()) {
                    log.warn("Excel field count validation failed: {}", String.join("; ", countValidation.getErrors()));
                    return ResponseUtil.error("Excel validation failed: " + String.join("; ", countValidation.getErrors()),
                            HttpStatus.BAD_REQUEST);
                }
                
                // Validate headers for Excel
                CsvValidationUtil.ValidationResult headerValidation = CsvValidationUtil.validateHeaders(headers, activeCanonicalFields);
                if (!headerValidation.isValid()) {
                    log.warn("Excel header validation failed: {}", String.join("; ", headerValidation.getErrors()));
                    return ResponseUtil.error("Excel validation failed: " + String.join("; ", headerValidation.getErrors()),
                            HttpStatus.BAD_REQUEST);
                }
                
                // Create field map for Excel data type validation
                Map<String, CanonicalField> fieldMap = new HashMap<>();
                for (CanonicalField field : activeCanonicalFields) {
                    String normalizedName = field.getFieldName().toLowerCase().trim();
                    fieldMap.put(normalizedName, field);
                }
                
                List<Map<String, Object>> excelInvalidRows = new ArrayList<>();
                
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    
                    Map<String, String> rowData = new HashMap<>();
                    for (int j = 0; j < headers.size(); j++) {
                        Cell cell = row.getCell(j);
                        rowData.put(headers.get(j), getCellValueAsString(cell));
                    }
                    
                    // Validate row data types and required fields
                    CsvValidationUtil.ValidationResult rowValidation = CsvValidationUtil.validateRow(rowData, fieldMap, headerMapping);
                    if (!rowValidation.isValid()) {
                        log.warn("Row {} failed Excel datatype validation: {}", i, String.join("; ", rowValidation.getErrors()));
                        excelInvalidRows.add(Map.of(
                                "rowNumber", i,
                                "reason", String.join("; ", rowValidation.getErrors()),
                                "rawInput", rowData
                        ));
                        continue;
                    }
                    
                    Map<String, String> normalized = LeadNormalizationUtil.normalizeRowValues(rowData, headerMapping);
                    
                    if (LeadNormalizationUtil.validateIdentifiers(normalized)) {
                        int rowNumber = i; // 1-based excluding header (header is row 0)
                        rows.add(new RowCtx(rowNumber, rowData, normalized));
                    } else {
                        log.warn("Row {} failed identifier validation (Excel): rawKeys={}, normalizedKeys={}",
                                i, rowData.keySet(), normalized.keySet());
                        excelInvalidRows.add(Map.of(
                                "rowNumber", i,
                                "reason", "At least one identifier (phone_number, email, or aadhar_number) is required",
                                "rawInput", rowData
                        ));
                    }
                }
                
                // Return error if any Excel rows failed validation
                if (!excelInvalidRows.isEmpty()) {
                    log.warn("Lead upload rejected: Excel file has invalid rows (invalidCount={})", excelInvalidRows.size());
                    return ResponseUtil.error("Excel file contains validation errors",
                            HttpStatus.BAD_REQUEST,
                            excelInvalidRows.stream().limit(100).collect(Collectors.toList()));
                }
                
                workbook.close();
            } else {
                return ResponseUtil.error("Unsupported file format. Use CSV or Excel (.xlsx, .xls)",
                        HttpStatus.BAD_REQUEST);
            }
            
            if (rows.isEmpty()) {
                log.warn("Lead upload rejected: no valid data rows after parsing (p_id={}, source_id={})",
                        pIdUpper, sourceIdUpper);
                return ResponseUtil.error("File contains no valid data rows",
                        HttpStatus.BAD_REQUEST);
            }
            
            int insertedCount = 0;
            int mergedCount = 0;
            int failedCount = 0;
            List<Map<String, Object>> failedRows = new ArrayList<>();
            
            for (int i = 0; i < rows.size(); i++) {
                RowCtx rowCtx = rows.get(i);
                Map<String, String> normalized = rowCtx.normalized;
                try {
                    LeadService.UpsertContext ctx = new LeadService.UpsertContext(
                            pIdUpper,
                            sourceIdUpper,
                            normalized
                    );
                    
                    LeadService.UpsertResult result = leadService.upsertLead(normalized, ctx);
                    
                    if ("inserted".equals(result.getAction())) {
                        insertedCount++;
                    } else {
                        mergedCount++;
                    }
                } catch (Exception e) {
                    failedCount++;
                    String reason = e.getMessage() != null ? e.getMessage() : "Processing error";
                    log.error("Row {} failed during upsert: {}", rowCtx.rowNumber, reason, e);
                    failedRows.add(Map.of(
                            "rowNumber", rowCtx.rowNumber,           // Frontend expects rowNumber
                            "reason", reason,
                            "rawInput", rowCtx.raw                   // For debugging
                    ));
                }
            }
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("totalRows", rows.size());
            responseData.put("insertedCount", insertedCount);
            responseData.put("mergedCount", mergedCount);
            responseData.put("failedCount", failedCount);
            responseData.put("failedRows", failedRows.size() > 100 
                    ? failedRows.subList(0, 100) : failedRows);

            log.info("Lead upload completed: totalRows={}, insertedCount={}, mergedCount={}, failedCount={} (p_id={}, source_id={})",
                    rows.size(), insertedCount, mergedCount, failedCount, pIdUpper, sourceIdUpper);

            // Run automatic deduplication per product using each product's configured dedup fields
            try {
                Map<String, DeduplicationService.DeduplicationStats> perProductStats =
                        deduplicationService.executeDeduplicationForAllProducts();
                long totalLeadsBefore = 0;
                long duplicatesFound = 0;
                int dedupMergedCount = 0;
                for (DeduplicationService.DeduplicationStats s : perProductStats.values()) {
                    if (s != null) {
                        totalLeadsBefore += s.getTotalLeads();
                        duplicatesFound += s.getDuplicatesFound();
                        dedupMergedCount += s.getMergedCount();
                    }
                }
                long finalLeadCount = leadRepository.count();
                responseData.put("deduplication", Map.of(
                        "totalLeadsBefore", totalLeadsBefore,
                        "duplicatesFound", duplicatesFound,
                        "mergedCount", dedupMergedCount,
                        "finalLeadCount", finalLeadCount
                ));
                log.info("Automatic per-product deduplication after upload: mergedCount={}, finalLeadCount={}",
                        dedupMergedCount, finalLeadCount);
            } catch (Exception e) {
                log.warn("Automatic deduplication after upload failed (upload succeeded): {}", e.getMessage());
                responseData.put("deduplication", Map.of(
                        "error", e.getMessage() != null ? e.getMessage() : "Deduplication failed"
                ));
            }
            
            return ResponseUtil.success(responseData, "Upload completed");
        } catch (Exception e) {
            log.error("Lead upload failed with exception: {}", e.getMessage(), e);
            return ResponseUtil.error("Failed to process file: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf((long) cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    private Integer parseIntegerOrNull(Object value, String fieldName) {
        if (value == null) return null;
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) return null;
            try {
                return Integer.valueOf(trimmed);
            } catch (NumberFormatException ex) {
                throw new RuntimeException("Invalid integer for '" + fieldName + "'");
            }
        }
        throw new RuntimeException("Invalid value type for '" + fieldName + "'");
    }

    private Boolean parseBooleanOrNull(Object value, String fieldName) {
        if (value == null) return null;
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            int asInt = number.intValue();
            if (asInt == 0) return Boolean.FALSE;
            if (asInt == 1) return Boolean.TRUE;
            throw new RuntimeException("Invalid numeric value for '" + fieldName + "'. Use 0 or 1");
        }
        if (value instanceof String str) {
            String trimmed = str.trim().toLowerCase();
            if (trimmed.isEmpty()) return null;
            if ("true".equals(trimmed) || "1".equals(trimmed)) return Boolean.TRUE;
            if ("false".equals(trimmed) || "0".equals(trimmed)) return Boolean.FALSE;
            throw new RuntimeException("Invalid boolean value for '" + fieldName + "'");
        }
        throw new RuntimeException("Invalid value type for '" + fieldName + "'");
    }

    private Lead.EmploymentType parseEmploymentTypeOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof Lead.EmploymentType employmentType) {
            return employmentType;
        }
        if (value instanceof String str) {
            String trimmed = str.trim();
            if (trimmed.isEmpty()) return null;
            try {
                return Lead.EmploymentType.valueOf(trimmed.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException("Invalid employmentType. Allowed values: SALARIED, SELF_EMPLOYED, OTHER");
            }
        }
        throw new RuntimeException("Invalid value type for 'employmentType'");
    }
    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<LeadDTO>>> getLeads(
            @RequestParam(required = false) String p_id,
            @RequestParam(required = false) String source_id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order) {
        
        Pageable pageable = PageRequest.of(page - 1, Math.min(10000, Math.max(1, limit)),
                Sort.by("desc".equalsIgnoreCase(order) ? Sort.Direction.DESC : Sort.Direction.ASC, sort));
        
        Query query = new Query();
        
        if (p_id != null) {
            query.addCriteria(Criteria.where("pId").is(p_id.toUpperCase()));
        }
        
        if (source_id != null) {
            query.addCriteria(Criteria.where("sourceId").is(source_id.toUpperCase()));
        }
        
        if (from != null || to != null) {
            Criteria dateCriteria = Criteria.where("createdAt");
            if (from != null) {
                dateCriteria.gte(LocalDateTime.parse(from));
            }
            if (to != null) {
                dateCriteria.lte(LocalDateTime.parse(to));
            }
            query.addCriteria(dateCriteria);
        }
        
        if (q != null && !q.trim().isEmpty()) {
            String searchTerm = q.trim();
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(searchTerm, "i"),
                    Criteria.where("email").regex(searchTerm, "i"),
                    Criteria.where("phoneNumber").regex(searchTerm, "i")
            ));
        }
        
        long total = mongoTemplate.count(query, Lead.class);
        List<Lead> leads = mongoTemplate.find(query.with(pageable), Lead.class);
        
        // Enrich leads with product and source names
        List<LeadDTO> enrichedLeads = leads.stream().map(lead -> {
            String productName = lead.getPId() != null ? 
                productRepository.findByPId(lead.getPId())
                    .map(Product::getPName)
                    .orElse("") : "";
            
            String sourceName = lead.getSourceId() != null ? 
                sourceRepository.findBySourceId(lead.getSourceId())
                    .map(Source::getSName)
                    .orElse("") : "";
            
            return LeadDTO.builder()
                    .leadId(lead.getLeadId())
                    .name(lead.getName())
                    .email(lead.getEmail())
                    .phoneNumber(lead.getPhoneNumber())
                    .aadharNumber(lead.getAadharNumber())
                    .pId(lead.getPId())
                    .productName(productName)
                    .sourceId(lead.getSourceId())
                    .sourceName(sourceName)
                    .createdAt(lead.getCreatedAt())
                    .income(lead.getIncome())
                    .creditScore(lead.getCreditScore())
                    .employmentType(lead.getEmploymentType())
                    .loanAmount(lead.getLoanAmount())
                    .converted(lead.getConverted())
                    .build();
        }).collect(Collectors.toList());
        
        Page<LeadDTO> leadPage = new PageImpl<>(enrichedLeads, pageable, total);
        
        ApiResponse<Page<LeadDTO>> response = new ApiResponse<>();
        response.setData(leadPage);
        response.setMessage("Leads retrieved successfully");
        response.setSuccess(true);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Lead>> getLeadById(@PathVariable String id) {
        return leadRepository.findByLeadId(id)
                .map(lead -> ResponseUtil.success(lead))
                .orElse(ResponseUtil.error("Lead with lead_id '" + id + "' not found",
                        HttpStatus.NOT_FOUND));
    }
    
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<Lead>> createLead(@Valid @RequestBody CreateLeadRequest request) {
        if (request.getPhoneNumber() == null && request.getEmail() == null && request.getAadharNumber() == null) {
            return ResponseUtil.error("At least one identifier (phone_number, email, or aadhar_number) is required",
                    HttpStatus.BAD_REQUEST);
        }
        
        if (request.getPId() != null && !productRepository.existsByPId(request.getPId().toUpperCase())) {
            return ResponseUtil.error("Product '" + request.getPId() + "' not found",
                    HttpStatus.BAD_REQUEST);
        }
        
        if (request.getSourceId() != null && !sourceRepository.existsBySourceId(request.getSourceId().toUpperCase())) {
            return ResponseUtil.error("Source '" + request.getSourceId() + "' not found",
                    HttpStatus.BAD_REQUEST);
        }
        
        Map<String, String> normalized = new HashMap<>();
        if (request.getName() != null) normalized.put("name", request.getName().trim());
        if (request.getPhoneNumber() != null) {
            normalized.put("phone_number", LeadNormalizationUtil.normalizePhone(request.getPhoneNumber()));
        }
        if (request.getEmail() != null) {
            normalized.put("email", LeadNormalizationUtil.normalizeEmail(request.getEmail()));
        }
        if (request.getAadharNumber() != null) {
            normalized.put("aadhar_number", LeadNormalizationUtil.normalizeAadhar(request.getAadharNumber()));
        }
        
        LeadService.UpsertContext ctx = new LeadService.UpsertContext(
                request.getPId() != null ? request.getPId().toUpperCase() : null,
                request.getSourceId() != null ? request.getSourceId().toUpperCase() : null,
                normalized,
                request.getIncome(),
                request.getCreditScore(),
                request.getEmploymentType(),
                request.getLoanAmount(),
                request.getConverted()
        );
        
        LeadService.UpsertResult result = leadService.upsertLead(normalized, ctx);
        
        return ResponseUtil.success(result.getLead(), "Lead created successfully",
                HttpStatus.CREATED);
    }
    
    
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Lead>> updateLead(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {
        return leadRepository.findByLeadId(id)
                .map(lead -> {
                    if (updates.containsKey("name")) {
                        lead.setName((String) updates.get("name"));
                    }
                    if (updates.containsKey("phone_number")) {
                        String phone = (String) updates.get("phone_number");
                        lead.setPhoneNumber(phone != null ? LeadNormalizationUtil.normalizePhone(phone) : null);
                    }
                    if (updates.containsKey("email")) {
                        String email = (String) updates.get("email");
                        lead.setEmail(email != null ? LeadNormalizationUtil.normalizeEmail(email) : null);
                    }
                    if (updates.containsKey("aadhar_number")) {
                        String aadhar = (String) updates.get("aadhar_number");
                        lead.setAadharNumber(aadhar != null ? LeadNormalizationUtil.normalizeAadhar(aadhar) : null);
                    }
                    if (updates.containsKey("p_id") && updates.get("p_id") != null) {
                        String pId = ((String) updates.get("p_id")).toUpperCase();
                        if (!productRepository.existsByPId(pId)) {
                            throw new RuntimeException("Product '" + updates.get("p_id") + "' not found");
                        }
                        lead.setPId(pId);
                        if (!lead.getProductsSeen().contains(pId)) {
                            lead.getProductsSeen().add(pId);
                        }
                    }
                    if (updates.containsKey("source_id") && updates.get("source_id") != null) {
                        String sourceId = ((String) updates.get("source_id")).toUpperCase();
                        if (!sourceRepository.existsBySourceId(sourceId)) {
                            throw new RuntimeException("Source '" + updates.get("source_id") + "' not found");
                        }
                        lead.setSourceId(sourceId);
                        if (!lead.getSourcesSeen().contains(sourceId)) {
                            lead.getSourcesSeen().add(sourceId);
                        }
                    }
                    if (updates.containsKey("income")) {
                        lead.setIncome(parseIntegerOrNull(updates.get("income"), "income"));
                    }
                    if (updates.containsKey("creditScore")) {
                        lead.setCreditScore(parseIntegerOrNull(updates.get("creditScore"), "creditScore"));
                    }
                    if (updates.containsKey("employmentType")) {
                        lead.setEmploymentType(parseEmploymentTypeOrNull(updates.get("employmentType")));
                    }
                    if (updates.containsKey("loanAmount")) {
                        lead.setLoanAmount(parseIntegerOrNull(updates.get("loanAmount"), "loanAmount"));
                    }
                    if (updates.containsKey("converted")) {
                        lead.setConverted(parseBooleanOrNull(updates.get("converted"), "converted"));
                    }
                    
                    lead.setUpdatedAt(LocalDateTime.now());
                    Lead saved = leadRepository.save(lead);
                    return ResponseUtil.success(saved, "Lead updated successfully");
                })
                .orElse(ResponseUtil.error("Lead with lead_id '" + id + "' not found",
                        HttpStatus.NOT_FOUND));
    }
    
    
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteLead(@PathVariable String id) {
        return leadRepository.findByLeadId(id)
                .map(lead -> {
                    leadRepository.delete(lead);
                    return ResponseUtil.success((Object) Map.of("message", "Lead deleted successfully"));
                })
                .orElse(ResponseUtil.error("Lead with lead_id '" + id + "' not found",
                        HttpStatus.NOT_FOUND));
    }
    
    @GetMapping("/{id}/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLeadHistory(@PathVariable String id) {
        return leadRepository.findByLeadId(id)
                .map(lead -> {
                    Map<String, Object> history = new HashMap<>();
                    history.put("lead_id", lead.getLeadId());
                    history.put("merged_from", lead.getMergedFrom());
                    history.put("sources_seen", lead.getSourcesSeen());
                    history.put("products_seen", lead.getProductsSeen());
                    history.put("created_at", lead.getCreatedAt());
                    return ResponseUtil.success(history);
                })
                .orElse(ResponseUtil.error("Lead with lead_id '" + id + "' not found",
                        HttpStatus.NOT_FOUND));
    }
    
    @PostMapping("/{id}/score")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scoreLead(@PathVariable String id) {
        return leadRepository.findByLeadId(id)
                .map(lead -> {
                    LeadScoringService.ScoringResult result = leadScoringService.scoreLead(lead);
                    
                    Map<String, Object> response = new HashMap<>();
                    response.put("lead_id", lead.getLeadId());
                    response.put("lead_score", result.getScore());
                    response.put("score_reason", result.getReason());
                    response.put("breakdown", result.getBreakdown());
                    
                    return ResponseUtil.success(response, "Lead scored successfully");
                })
                .orElse(ResponseUtil.error("Lead with lead_id '" + id + "' not found",
                        HttpStatus.NOT_FOUND));
    }
}
