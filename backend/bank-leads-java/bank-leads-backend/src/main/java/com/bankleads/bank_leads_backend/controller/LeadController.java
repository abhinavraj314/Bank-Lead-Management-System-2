package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.request.CreateLeadRequest;
import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.dto.response.LeadDTO;
import com.bankleads.bank_leads_backend.model.CanonicalField;
import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.model.Product;
import com.bankleads.bank_leads_backend.model.Source;
import com.bankleads.bank_leads_backend.model.LeadEvent;
import com.bankleads.bank_leads_backend.model.User;
import com.bankleads.bank_leads_backend.repository.CanonicalFieldRepository;
import com.bankleads.bank_leads_backend.repository.LeadRepository;
import com.bankleads.bank_leads_backend.repository.ProductRepository;
import com.bankleads.bank_leads_backend.repository.SourceRepository;
import com.bankleads.bank_leads_backend.repository.UserRepository;
import com.bankleads.bank_leads_backend.service.CanonicalFieldDeduplicationService;
import com.bankleads.bank_leads_backend.service.DeduplicationService;
import com.bankleads.bank_leads_backend.service.LeadScoringService;
import com.bankleads.bank_leads_backend.service.LeadService;
import com.bankleads.bank_leads_backend.service.LeadAuditService;
import com.bankleads.bank_leads_backend.service.LeadStateService;
import com.bankleads.bank_leads_backend.service.WorkflowService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leads")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class LeadController {

    private static final Logger log = LoggerFactory.getLogger(LeadController.class);

    /** Indexed Mongo fields only — avoids arbitrary sort injection. */
    private static final Set<String> ALLOWED_LEAD_SORT_FIELDS = Set.of(
            "createdAt", "leadScore", "updatedAt", "statusUpdatedAt");

    private static final List<Lead.LeadStatus> TERMINAL_STATUSES_FOR_FILTER = List.of(
            Lead.LeadStatus.CONVERTED,
            Lead.LeadStatus.NOT_CONVERTED,
            Lead.LeadStatus.CLOSED);
    
    private final LeadRepository leadRepository;
    private final ProductRepository productRepository;
    private final SourceRepository sourceRepository;
    private final CanonicalFieldRepository canonicalFieldRepository;
    private final UserRepository userRepository;
    private final LeadService leadService;
    private final LeadScoringService leadScoringService;
    private final CanonicalFieldDeduplicationService canonicalFieldDeduplicationService;
    private final DeduplicationService deduplicationService;
    private final LeadStateService leadStateService;
    private final WorkflowService workflowService;
    private final LeadAuditService leadAuditService;
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
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assigned_user_id,
            @RequestParam(required = false) Boolean assigned_to_me,
            @RequestParam(required = false) Boolean hide_terminal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "25") int limit,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            HttpServletRequest request) {

        String sortField = ALLOWED_LEAD_SORT_FIELDS.contains(sort) ? sort : "createdAt";
        Pageable pageable = PageRequest.of(page - 1, Math.min(200, Math.max(1, limit)),
                Sort.by("desc".equalsIgnoreCase(order) ? Sort.Direction.DESC : Sort.Direction.ASC, sortField));
        
        // Get current user info for role-based filtering
        String currentUserId = getCurrentUserId(request);
        boolean isAdmin = isCurrentUserAdmin(request);
        
        Query query = new Query();
        
        // Role-based filtering: sales users see only their assigned leads
        if (!isAdmin && assigned_to_me == null && assigned_user_id == null) {
            // Default: sales users see only their leads
            if (currentUserId != null) {
                query.addCriteria(Criteria.where("assignedUserId").is(currentUserId));
            } else {
                // No user context: return empty (or could return unassigned only)
                query.addCriteria(Criteria.where("assignedUserId").is(null));
            }
        }
        
        if (p_id != null) {
            query.addCriteria(Criteria.where("pId").is(p_id.toUpperCase()));
        }
        
        if (source_id != null) {
            query.addCriteria(Criteria.where("sourceId").is(source_id.toUpperCase()));
        }
        
        // Status filter (match either legacy `status` or primary `state` field)
        if (status != null && !status.trim().isEmpty()) {
            try {
                Lead.LeadStatus statusEnum = Lead.LeadStatus.valueOf(status.trim().toUpperCase());
                query.addCriteria(new Criteria().orOperator(
                        Criteria.where("status").is(statusEnum),
                        Criteria.where("state").is(statusEnum)
                ));
            } catch (IllegalArgumentException e) {
                return ResponseUtil.error("Invalid status: " + status, HttpStatus.BAD_REQUEST);
            }
        }
        
        // Assignment filter
        if (assigned_to_me != null && assigned_to_me && currentUserId != null) {
            query.addCriteria(Criteria.where("assignedUserId").is(currentUserId));
        } else if (assigned_user_id != null) {
            if (!isAdmin) {
                return ResponseUtil.error("Only admins can filter by assigned_user_id", HttpStatus.FORBIDDEN);
            }
            if (assigned_user_id.trim().equalsIgnoreCase("unassigned") || assigned_user_id.trim().isEmpty()) {
                query.addCriteria(Criteria.where("assignedUserId").is(null));
            } else {
                query.addCriteria(Criteria.where("assignedUserId").is(assigned_user_id.trim()));
            }
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
            if (searchTerm.length() > 200) {
                searchTerm = searchTerm.substring(0, 200);
            }
            String escaped = Pattern.quote(searchTerm);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(escaped, "i"),
                    Criteria.where("email").regex(escaped, "i"),
                    Criteria.where("phoneNumber").regex(escaped, "i")
            ));
        }

        if (Boolean.TRUE.equals(hide_terminal)) {
            query.addCriteria(new Criteria().norOperator(
                    Criteria.where("state").in(TERMINAL_STATUSES_FOR_FILTER),
                    new Criteria().andOperator(
                            Criteria.where("state").is(null),
                            Criteria.where("status").in(TERMINAL_STATUSES_FOR_FILTER))));
        }
        
        long total = mongoTemplate.count(query, Lead.class);
        List<Lead> leads = mongoTemplate.find(query.with(pageable), Lead.class);
        
        // Batch load product and source names (avoid N+1)
        Set<String> pIds = leads.stream().map(Lead::getPId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> sourceIds = leads.stream().map(Lead::getSourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> assignedUserIds = leads.stream().map(Lead::getAssignedUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        
        Map<String, String> productNames = productRepository.findByPIdIn(pIds).stream()
                .collect(Collectors.toMap(Product::getPId, Product::getPName, (a, b) -> a));
        Map<String, String> sourceNames = sourceRepository.findBySIdIn(sourceIds).stream()
                .collect(Collectors.toMap(Source::getSId, Source::getSName, (a, b) -> a));
        Map<String, String> userNames = userRepository.findAllById(assignedUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getUsername() != null ? u.getUsername() : u.getEmail(), (a, b) -> a));
        
        Map<String, String> productNamesFinal = productNames;
        Map<String, String> sourceNamesFinal = sourceNames;
        Map<String, String> userNamesFinal = userNames;
        List<LeadDTO> enrichedLeads = leads.stream().map(lead -> {
            String productName = lead.getPId() != null ? productNamesFinal.getOrDefault(lead.getPId(), "") : "";
            String sourceName = lead.getSourceId() != null ? sourceNamesFinal.getOrDefault(lead.getSourceId(), "") : "";
            String assignedUserName = lead.getAssignedUserId() != null ? userNamesFinal.getOrDefault(lead.getAssignedUserId(), "") : "";
            
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
                    .leadScore(lead.getLeadScore())
                    .scoreReason(lead.getScoreReason())
                    .status(lead.getStatus())
                    .state(lead.getState())
                    .assignedUserId(lead.getAssignedUserId())
                    .assignedUserName(assignedUserName)
                    .statusUpdatedAt(lead.getStatusUpdatedAt())
                    .assignedAt(lead.getAssignedAt())
                    .allowedNextStates(workflowService.allowedNextStates(lead).stream()
                            .map(Enum::name)
                            .collect(Collectors.toList()))
                    .teamId(lead.getTeamId())
                    .scoreBreakdown(lead.getScoreBreakdown())
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
        log.debug("[DEBUG] Raw request received: {}", request);
        log.debug("[DEBUG] pId raw value: '{}' (type: {}, isNull: {}, length: {})", 
                request.getPId(), 
                request.getPId() != null ? request.getPId().getClass().getName() : "null",
                request.getPId() == null,
                request.getPId() != null ? request.getPId().length() : "N/A");
        log.debug("[DEBUG] sourceId raw value: '{}' (type: {}, isNull: {}, length: {})", 
                request.getSourceId(), 
                request.getSourceId() != null ? request.getSourceId().getClass().getName() : "null",
                request.getSourceId() == null,
                request.getSourceId() != null ? request.getSourceId().length() : "N/A");
        log.info("Received lead creation request: name={}, email={}, phone={}, aadhar={}, pId={}, sourceId={}",
                request.getName(), request.getEmail(), request.getPhoneNumber(), request.getAadharNumber(),
                request.getPId(), request.getSourceId());
        
        // Validate at least one identifier
        if (request.getPhoneNumber() == null && request.getEmail() == null && request.getAadharNumber() == null) {
            log.warn("Lead creation failed: no identifiers provided");
            return ResponseUtil.error("At least one identifier (phone_number, email, or aadhar_number) is required",
                    HttpStatus.BAD_REQUEST);
        }
        
        // Validate phone number format if provided
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().matches("^\\d{10}$")) {
            log.warn("Lead creation failed: invalid phone number format: {}", request.getPhoneNumber());
            return ResponseUtil.error("Phone number must be exactly 10 digits",
                    HttpStatus.BAD_REQUEST);
        }
        
        // Validate aadhar number format if provided
        if (request.getAadharNumber() != null && !request.getAadharNumber().matches("^\\d{12}$")) {
            log.warn("Lead creation failed: invalid aadhar number format: {}", request.getAadharNumber());
            return ResponseUtil.error("Aadhar number must be exactly 12 digits",
                    HttpStatus.BAD_REQUEST);
        }
        
        // Validate product exists (pId is guaranteed non-null by @NotBlank)
        if (!productRepository.existsByPId(request.getPId().toUpperCase())) {
            log.warn("Lead creation failed: product not found (pId={})", request.getPId());
            return ResponseUtil.error("Product '" + request.getPId() + "' not found",
                    HttpStatus.BAD_REQUEST);
        }
        
        // Validate source exists (sourceId is guaranteed non-null by @NotBlank)
        if (!sourceRepository.existsBySourceId(request.getSourceId().toUpperCase())) {
            log.warn("Lead creation failed: source not found (sourceId={})", request.getSourceId());
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
        log.info("Lead created successfully: leadId={}", result.getLead().getLeadId());
        
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
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLeadHistory(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return leadRepository.findByLeadId(id)
                .map(lead -> {
                    var eventPage = leadAuditService.listHistory(id, page, size);
                    Map<String, Object> history = new HashMap<>();
                    history.put("leadId", lead.getLeadId());
                    history.put("events", eventPage.getContent());
                    history.put("totalElements", eventPage.getTotalElements());
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
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/score-all")
    public ResponseEntity<ApiResponse<Map<String, Object>>> scoreAllLeads() {
        List<Lead> allLeads = leadRepository.findAll();
        log.info("Batch scoring {} leads", allLeads.size());
        
        int scoredCount = leadScoringService.batchScoreLeads(allLeads);
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalLeads", allLeads.size());
        result.put("scoredCount", scoredCount);
        
        log.info("Batch scoring completed: {}/{} leads scored", scoredCount, allLeads.size());
        return ResponseUtil.success(result, "Leads scored successfully");
    }
    
    @GetMapping("/ml-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMlStatus() {
        Map<String, Object> status = new HashMap<>();
        boolean available = leadScoringService.isMlServiceAvailable();
        status.put("mlServiceAvailable", available);
        status.put("scoringMethod", available
                ? "ML service connected"
                : "Heuristic fallback (ML service unreachable)");
        return ResponseUtil.success(status);
    }
    
    // ==================== Lead Lifecycle & Assignment ====================
    
    /**
     * Update lead status (lifecycle state).
     * Sales users can only update leads assigned to them and only valid transitions.
     * Admins can make any transition.
     */
    @PatchMapping("/{id}/state")
    public ResponseEntity<ApiResponse<LeadDTO>> updateLeadState(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        
        String statusStr = body.get("status");
        if (statusStr == null || statusStr.trim().isEmpty()) {
            return ResponseUtil.error("status is required", HttpStatus.BAD_REQUEST);
        }
        
        Lead.LeadStatus newStatus;
        try {
            newStatus = Lead.LeadStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error("Invalid status: " + statusStr, HttpStatus.BAD_REQUEST);
        }
        
        return leadRepository.findByLeadId(id)
                .map(lead -> {
                    String currentUserId = getCurrentUserId(request);
                    boolean isAdmin = isCurrentUserAdmin(request);
                    Lead.LeadStatus fromNorm = workflowService.normalizeCurrentState(lead);

                    // Check permission
                    if (!leadStateService.canUpdateState(lead, currentUserId, isAdmin)) {
                        return ResponseUtil.<LeadDTO>error(
                            "You can only update leads assigned to you",
                            HttpStatus.FORBIDDEN
                        );
                    }
                    
                    // Validate and update state
                    try {
                        leadStateService.updateStatus(lead, newStatus, isAdmin);
                        leadRepository.save(lead);
                        Lead.LeadStatus toNorm = workflowService.normalizeCurrentState(lead);
                        if (fromNorm != toNorm) {
                            leadAuditService.append(lead.getLeadId(), LeadEvent.EventType.STATE_CHANGED, currentUserId,
                                    Map.of("from", fromNorm.name(), "to", toNorm.name()));
                        }

                        // Return enriched DTO
                        return enrichLeadToDTO(lead)
                                .map(dto -> ResponseUtil.success(dto, "Lead status updated"))
                                .orElse(ResponseUtil.<LeadDTO>error("Failed to enrich lead", HttpStatus.INTERNAL_SERVER_ERROR));
                    } catch (IllegalArgumentException e) {
                        return ResponseUtil.<LeadDTO>error(e.getMessage(), HttpStatus.BAD_REQUEST);
                    }
                })
                .orElse(ResponseUtil.<LeadDTO>error("Lead not found: " + id, HttpStatus.NOT_FOUND));
    }
    
    /**
     * Update lead assignment.
     * Admins can assign to any user or unassign (null).
     * Sales users can self-assign unassigned leads only.
     */
    @PatchMapping("/{id}/assignment")
    public ResponseEntity<ApiResponse<LeadDTO>> updateLeadAssignment(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        
        String assignedUserId = body.get("assignedUserId");
        String currentUserId = getCurrentUserId(request);
        boolean isAdmin = isCurrentUserAdmin(request);
        
        return leadRepository.findByLeadId(id)
                .map(lead -> {
                    String prevAssigned = lead.getAssignedUserId();

                    // Sales users: can only self-assign unassigned leads
                    if (!isAdmin) {
                        if (lead.getAssignedUserId() != null) {
                            return ResponseUtil.<LeadDTO>error(
                                "Lead is already assigned. Only admins can reassign.",
                                HttpStatus.FORBIDDEN
                            );
                        }
                        if (assignedUserId == null || !assignedUserId.equals(currentUserId)) {
                            return ResponseUtil.<LeadDTO>error(
                                "You can only assign leads to yourself",
                                HttpStatus.FORBIDDEN
                            );
                        }
                    }
                    
                    // Admin: can assign to any user or unassign
                    if (isAdmin && assignedUserId != null && !assignedUserId.trim().isEmpty()) {
                        // Verify user exists
                        if (!userRepository.existsById(assignedUserId.trim())) {
                            return ResponseUtil.<LeadDTO>error("User not found: " + assignedUserId, HttpStatus.BAD_REQUEST);
                        }
                        User user = userRepository.findById(assignedUserId.trim()).orElse(null);
                        lead.setAssignedUserId(assignedUserId.trim());
                        lead.setAssignedUserName(user != null ? (user.getUsername() != null ? user.getUsername() : user.getEmail()) : null);
                    } else {
                        // Unassign
                        lead.setAssignedUserId(null);
                        lead.setAssignedUserName(null);
                    }
                    
                    lead.setAssignedAt(LocalDateTime.now());
                    leadRepository.save(lead);

                    if (!Objects.equals(prevAssigned, lead.getAssignedUserId())) {
                        String fromUserName = "";
                        String toUserName = "";
                        if (prevAssigned != null) {
                            User prevUser = userRepository.findById(prevAssigned).orElse(null);
                            fromUserName = prevUser != null ? (prevUser.getUsername() != null ? prevUser.getUsername() : prevUser.getEmail()) : "";
                        }
                        if (lead.getAssignedUserId() != null) {
                            User toUser = userRepository.findById(lead.getAssignedUserId()).orElse(null);
                            toUserName = toUser != null ? (toUser.getUsername() != null ? toUser.getUsername() : toUser.getEmail()) : "";
                        }
                        leadAuditService.append(lead.getLeadId(), LeadEvent.EventType.ASSIGNMENT_CHANGED, currentUserId,
                                Map.of(
                                        "fromUserId", fromUserName,
                                        "toUserId", toUserName));
                    }

                    return enrichLeadToDTO(lead)
                            .map(dto -> ResponseUtil.success(dto, "Lead assignment updated"))
                            .orElse(ResponseUtil.<LeadDTO>error("Failed to enrich lead", HttpStatus.INTERNAL_SERVER_ERROR));
                })
                .orElse(ResponseUtil.<LeadDTO>error("Lead not found: " + id, HttpStatus.NOT_FOUND));
    }
    
    /**
     * Self-assign an unassigned lead (convenience endpoint for sales users).
     */
    @PatchMapping("/{id}/assignment/self")
    public ResponseEntity<ApiResponse<LeadDTO>> selfAssignLead(
            @PathVariable String id,
            HttpServletRequest request) {
        
        String currentUserId = getCurrentUserId(request);
        if (currentUserId == null) {
            return ResponseUtil.error("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        
        return leadRepository.findByLeadId(id)
                .map(lead -> {
                    if (lead.getAssignedUserId() != null) {
                        return ResponseUtil.<LeadDTO>error(
                            "Lead is already assigned to: " + lead.getAssignedUserId(),
                            HttpStatus.BAD_REQUEST
                        );
                    }
                    
                    User user = userRepository.findById(currentUserId).orElse(null);
                    if (user == null) {
                        return ResponseUtil.<LeadDTO>error("User not found", HttpStatus.NOT_FOUND);
                    }

                    String prevAssigned = lead.getAssignedUserId();
                    lead.setAssignedUserId(currentUserId);
                    lead.setAssignedUserName(user.getUsername() != null ? user.getUsername() : user.getEmail());
                    lead.setAssignedAt(LocalDateTime.now());
                    leadRepository.save(lead);

                    if (!Objects.equals(prevAssigned, lead.getAssignedUserId())) {
                        String toUserName = "";
                        if (lead.getAssignedUserId() != null) {
                            User toUser = userRepository.findById(lead.getAssignedUserId()).orElse(null);
                            toUserName = toUser != null ? (toUser.getUsername() != null ? toUser.getUsername() : toUser.getEmail()) : "";
                        }
                        leadAuditService.append(lead.getLeadId(), LeadEvent.EventType.ASSIGNMENT_CHANGED, currentUserId,
                                Map.of(
                                        "fromUserId", "",
                                        "toUserId", toUserName));
                    }

                    return enrichLeadToDTO(lead)
                            .map(dto -> ResponseUtil.success(dto, "Lead assigned to you"))
                            .orElse(ResponseUtil.<LeadDTO>error("Failed to enrich lead", HttpStatus.INTERNAL_SERVER_ERROR));
                })
                .orElse(ResponseUtil.<LeadDTO>error("Lead not found: " + id, HttpStatus.NOT_FOUND));
    }
    
    /**
     * Combined update endpoint: update status and/or assignment in one call.
     * Useful for inline editing from the Leads tab.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<LeadDTO>> updateLead(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates,
            HttpServletRequest request) {
        
        String currentUserId = getCurrentUserId(request);
        boolean isAdmin = isCurrentUserAdmin(request);
        
        return leadRepository.findByLeadId(id)
                .map(lead -> {
                    // Check permission
                    if (!leadStateService.canUpdateState(lead, currentUserId, isAdmin)) {
                        return ResponseUtil.<LeadDTO>error(
                            "You can only update leads assigned to you",
                            HttpStatus.FORBIDDEN
                        );
                    }

                    Lead.LeadStatus fromNorm = workflowService.normalizeCurrentState(lead);
                    String prevAssigned = lead.getAssignedUserId();
                    
                    // Update status if provided
                    if (updates.containsKey("status")) {
                        Object statusObj = updates.get("status");
                        if (statusObj != null) {
                            try {
                                Lead.LeadStatus newStatus = Lead.LeadStatus.valueOf(statusObj.toString().trim().toUpperCase());
                                leadStateService.updateStatus(lead, newStatus, isAdmin);
                                Lead.LeadStatus toNorm = workflowService.normalizeCurrentState(lead);
                                if (fromNorm != toNorm) {
                                    leadAuditService.append(lead.getLeadId(), LeadEvent.EventType.STATE_CHANGED, currentUserId,
                                            Map.of("from", fromNorm.name(), "to", toNorm.name()));
                                    fromNorm = toNorm;
                                }
                            } catch (IllegalArgumentException e) {
                                return ResponseUtil.<LeadDTO>error("Invalid status: " + statusObj, HttpStatus.BAD_REQUEST);
                            }
                        }
                    }
                    
                    // Update assignment if provided (admin only for reassignment)
                    if (updates.containsKey("assignedUserId")) {
                        Object assignedUserIdObj = updates.get("assignedUserId");
                        String assignedUserId = assignedUserIdObj == null ? null : assignedUserIdObj.toString().trim();
                        
                        if (!isAdmin && lead.getAssignedUserId() != null && assignedUserId != null && !assignedUserId.equals(lead.getAssignedUserId())) {
                            return ResponseUtil.<LeadDTO>error(
                                "Only admins can reassign leads",
                                HttpStatus.FORBIDDEN
                            );
                        }
                        
                        if (assignedUserId == null || assignedUserId.isEmpty()) {
                            // Unassign
                            lead.setAssignedUserId(null);
                            lead.setAssignedUserName(null);
                        } else {
                            // Assign
                            if (!isAdmin && !assignedUserId.equals(currentUserId)) {
                                return ResponseUtil.<LeadDTO>error(
                                    "You can only assign leads to yourself",
                                    HttpStatus.FORBIDDEN
                                );
                            }
                            User user = userRepository.findById(assignedUserId).orElse(null);
                            if (user == null) {
                                return ResponseUtil.<LeadDTO>error("User not found: " + assignedUserId, HttpStatus.BAD_REQUEST);
                            }
                            lead.setAssignedUserId(assignedUserId);
                            lead.setAssignedUserName(user.getUsername() != null ? user.getUsername() : user.getEmail());
                        }
                        lead.setAssignedAt(LocalDateTime.now());
                    }

                    if (updates.containsKey("assignedUserId")
                            && !Objects.equals(prevAssigned, lead.getAssignedUserId())) {
                        String fromUserName = "";
                        String toUserName = "";
                        if (prevAssigned != null) {
                            User prevUser = userRepository.findById(prevAssigned).orElse(null);
                            fromUserName = prevUser != null ? (prevUser.getUsername() != null ? prevUser.getUsername() : prevUser.getEmail()) : "";
                        }
                        if (lead.getAssignedUserId() != null) {
                            User toUser = userRepository.findById(lead.getAssignedUserId()).orElse(null);
                            toUserName = toUser != null ? (toUser.getUsername() != null ? toUser.getUsername() : toUser.getEmail()) : "";
                        }
                        leadAuditService.append(lead.getLeadId(), LeadEvent.EventType.ASSIGNMENT_CHANGED, currentUserId,
                                Map.of(
                                        "fromUserId", fromUserName,
                                        "toUserId", toUserName));
                    }
                    
                    leadRepository.save(lead);
                    
                    return enrichLeadToDTO(lead)
                            .map(dto -> ResponseUtil.success(dto, "Lead updated"))
                            .orElse(ResponseUtil.<LeadDTO>error("Failed to enrich lead", HttpStatus.INTERNAL_SERVER_ERROR));
                })
                .orElse(ResponseUtil.<LeadDTO>error("Lead not found: " + id, HttpStatus.NOT_FOUND));
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Get current user ID from Authorization header (token is user ID).
     */
    private String getCurrentUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String username) {
            User user = userRepository.findByUsername(username).orElse(null);
            return user != null ? user.getId() : null;
        }
        return null;
    }
    
    /**
     * Check if current user is admin.
     */
    private boolean isCurrentUserAdmin(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }
    
    /**
     * Enrich a single lead to LeadDTO (with product/source/user names).
     */
    private Optional<LeadDTO> enrichLeadToDTO(Lead lead) {
        String productName = "";
        if (lead.getPId() != null) {
            productName = productRepository.findByPId(lead.getPId())
                    .map(Product::getPName)
                    .orElse("");
        }
        
        String sourceName = "";
        if (lead.getSourceId() != null) {
            sourceName = sourceRepository.findBySourceId(lead.getSourceId())
                    .map(Source::getSName)
                    .orElse("");
        }
        
        String assignedUserName = "";
        if (lead.getAssignedUserId() != null) {
            assignedUserName = userRepository.findById(lead.getAssignedUserId())
                    .map(u -> u.getUsername() != null ? u.getUsername() : u.getEmail())
                    .orElse("");
        }
        
        LeadDTO dto = LeadDTO.builder()
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
                .leadScore(lead.getLeadScore())
                .scoreReason(lead.getScoreReason())
                .status(lead.getStatus())
                .state(lead.getState())
                .assignedUserId(lead.getAssignedUserId())
                .assignedUserName(assignedUserName)
                .statusUpdatedAt(lead.getStatusUpdatedAt())
                .assignedAt(lead.getAssignedAt())
                .allowedNextStates(workflowService.allowedNextStates(lead).stream()
                        .map(Enum::name)
                        .collect(Collectors.toList()))
                .teamId(lead.getTeamId())
                .scoreBreakdown(lead.getScoreBreakdown())
                .build();
        
        return Optional.of(dto);
    }
}
