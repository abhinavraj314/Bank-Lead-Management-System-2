package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.request.CreateLeadRequest;
import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.repository.LeadRepository;
import com.bankleads.bank_leads_backend.repository.ProductRepository;
import com.bankleads.bank_leads_backend.repository.SourceRepository;
import com.bankleads.bank_leads_backend.service.LeadScoringService;
import com.bankleads.bank_leads_backend.service.LeadService;
import com.bankleads.bank_leads_backend.util.CsvParserUtil;
import com.bankleads.bank_leads_backend.util.LeadNormalizationUtil;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {
    
    private final LeadRepository leadRepository;
    private final ProductRepository productRepository;
    private final SourceRepository sourceRepository;
    private final LeadService leadService;
    private final LeadScoringService leadScoringService;
    private final MongoTemplate mongoTemplate;
    
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadLeads(
            @RequestParam("file") MultipartFile file,
            @RequestParam("p_id") String pId,
            @RequestParam("source_id") String sourceId) {
        
        if (file.isEmpty()) {
            return ResponseUtil.error("File is required", HttpStatus.BAD_REQUEST);
        }
        
        if (!productRepository.existsByPId(pId.toUpperCase())) {
            return ResponseUtil.error("Product '" + pId + "' not found",
                    HttpStatus.BAD_REQUEST);
        }
        
        if (!sourceRepository.existsBySourceId(sourceId.toUpperCase())) {
            return ResponseUtil.error("Source '" + sourceId + "' not found",
                    HttpStatus.BAD_REQUEST);
        }
        
        try {
            List<Map<String, String>> rows = new ArrayList<>();
            String filename = file.getOriginalFilename().toLowerCase();
            
            if (filename.endsWith(".csv")) {
                CsvParserUtil.ParseResult parseResult = CsvParserUtil.parseCSV(file.getBytes());
                
                if (!parseResult.isSuccess() || parseResult.getValidRows().isEmpty()) {
                    return ResponseUtil.error("Failed to parse CSV or no valid rows found",
                            HttpStatus.BAD_REQUEST,
                            parseResult.getInvalidRows().stream().map(r -> Map.of(
                                    "row", r.getRow(),
                                    "errors", r.getErrors()
                            )).collect(Collectors.toList()));
                }
                
                for (CsvParserUtil.ParsedRow parsedRow : parseResult.getValidRows()) {
                    rows.add(parsedRow.getData());
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
                
                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    
                    Map<String, String> rowData = new HashMap<>();
                    for (int j = 0; j < headers.size(); j++) {
                        Cell cell = row.getCell(j);
                        rowData.put(headers.get(j), getCellValueAsString(cell));
                    }
                    
                    Map<String, String> normalized = LeadNormalizationUtil.normalizeRowValues(rowData, headerMapping);
                    
                    if (LeadNormalizationUtil.validateIdentifiers(normalized)) {
                        rows.add(normalized);
                    }
                }
                
                workbook.close();
            } else {
                return ResponseUtil.error("Unsupported file format. Use CSV or Excel (.xlsx, .xls)",
                        HttpStatus.BAD_REQUEST);
            }
            
            if (rows.isEmpty()) {
                return ResponseUtil.error("File contains no valid data rows",
                        HttpStatus.BAD_REQUEST);
            }
            
            int insertedCount = 0;
            int mergedCount = 0;
            int failedCount = 0;
            List<Map<String, Object>> failedRows = new ArrayList<>();
            
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> normalized = rows.get(i);
                try {
                    LeadService.UpsertContext ctx = new LeadService.UpsertContext(
                            pId.toUpperCase(),
                            sourceId.toUpperCase(),
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
                    failedRows.add(Map.of(
                            "row", i + 1,
                            "reason", e.getMessage() != null ? e.getMessage() : "Processing error",
                            "raw", normalized
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
            
            return ResponseUtil.success(responseData, "Upload completed");
        } catch (Exception e) {
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
    
    @GetMapping
    public ResponseEntity<ApiResponse<Page<Lead>>> getLeads(
            @RequestParam(required = false) String p_id,
            @RequestParam(required = false) String source_id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order) {
        
        Pageable pageable = PageRequest.of(page - 1, Math.min(100, Math.max(1, limit)),
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
        
        Page<Lead> leadPage = new PageImpl<>(leads, pageable, total);
        
        // ✅ FIXED LINE 249 - Direct ResponseEntity creation
        ApiResponse<Page<Lead>> response = new ApiResponse<>();
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
                normalized
        );
        
        LeadService.UpsertResult result = leadService.upsertLead(normalized, ctx);
        
        return ResponseUtil.success(result.getLead(), "Lead created successfully",
                HttpStatus.CREATED);
    }
    
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
                    
                    lead.setUpdatedAt(LocalDateTime.now());
                    Lead saved = leadRepository.save(lead);
                    return ResponseUtil.success(saved, "Lead updated successfully");
                })
                .orElse(ResponseUtil.error("Lead with lead_id '" + id + "' not found",
                        HttpStatus.NOT_FOUND));
    }
    
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
