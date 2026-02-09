package com.bankleads.bank_leads_backend.util;

import com.bankleads.bank_leads_backend.model.CanonicalField;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CsvParserUtil {

    private static final Logger log = LoggerFactory.getLogger(CsvParserUtil.class);
    
    public static class ParseResult {
        private final boolean success;
        private final int totalRows;
        private final List<ParsedRow> validRows;
        private final List<ParsedRow> invalidRows;
        
        public ParseResult(boolean success, int totalRows, List<ParsedRow> validRows, List<ParsedRow> invalidRows) {
            this.success = success;
            this.totalRows = totalRows;
            this.validRows = validRows;
            this.invalidRows = invalidRows;
        }
        
        public boolean isSuccess() { return success; }
        public int getTotalRows() { return totalRows; }
        public List<ParsedRow> getValidRows() { return validRows; }
        public List<ParsedRow> getInvalidRows() { return invalidRows; }
    }
    
    public static class ParsedRow {
        private final int row;
        private final Map<String, String> raw;
        private final Map<String, String> data;
        private final List<String> errors;
        
        public ParsedRow(int row, Map<String, String> raw, Map<String, String> data, List<String> errors) {
            this.row = row;
            this.raw = raw;
            this.data = data;
            this.errors = errors;
        }
        
        public int getRow() { return row; }
        public Map<String, String> getRaw() { return raw; }
        public Map<String, String> getData() { return data; }
        public List<String> getErrors() { return errors; }
    }

    /**
     * Backwards-compatible parse method (no validation)
     */
    public static ParseResult parseCSV(byte[] fileBuffer) {
        return parseCSV(fileBuffer, null);
    }

    /**
     * Parse CSV with optional canonical field validation
     * @param fileBuffer CSV file content
     * @param canonicalFields List of canonical fields for validation (can be null for no validation)
     * @return ParseResult with validated rows
     */
    public static ParseResult parseCSV(byte[] fileBuffer, List<CanonicalField> canonicalFields) {
        try {
            String content = new String(fileBuffer, StandardCharsets.UTF_8);
            
            CSVParser parser = CSVParser.parse(content, CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .build());
            List<CSVRecord> records = parser.getRecords();
            
            if (records.isEmpty()) {
                return new ParseResult(false, 0, Collections.emptyList(),
                        Collections.singletonList(new ParsedRow(1, Collections.emptyMap(), Collections.emptyMap(),
                                Collections.singletonList("CSV file is empty or has no data rows"))));
            }
            
            List<String> headers = parser.getHeaderNames();
            log.info("CSV headers parsed: {}", headers);
            Map<String, String> headerMapping = LeadNormalizationUtil.normalizeHeaders(headers.toArray(new String[0]));
            log.info("CSV header mapping (original->canonical): {}", headerMapping);

            // If canonical fields provided, validate field count and headers first
            if (canonicalFields != null && !canonicalFields.isEmpty()) {
                CsvValidationUtil.ValidationResult countValidation = CsvValidationUtil.validateFieldCount(headers, canonicalFields);
                if (!countValidation.isValid()) {
                    return new ParseResult(false, 0, Collections.emptyList(),
                            Collections.singletonList(new ParsedRow(1, Collections.emptyMap(), Collections.emptyMap(), countValidation.getErrors())));
                }

                CsvValidationUtil.ValidationResult headerValidation = CsvValidationUtil.validateHeaders(headers, canonicalFields);
                if (!headerValidation.isValid()) {
                    return new ParseResult(false, 0, Collections.emptyList(),
                            Collections.singletonList(new ParsedRow(1, Collections.emptyMap(), Collections.emptyMap(), headerValidation.getErrors())));
                }
            }

            // Create field map for data type validation
            Map<String, CanonicalField> fieldMap = new HashMap<>();
            if (canonicalFields != null) {
                for (CanonicalField field : canonicalFields) {
                    if (field.getIsActive() != null && field.getIsActive()) {
                        String normalizedName = field.getFieldName().toLowerCase().trim();
                        fieldMap.put(normalizedName, field);
                    }
                }
            }

            List<ParsedRow> validRows = new ArrayList<>();
            List<ParsedRow> invalidRows = new ArrayList<>();

            for (int i = 0; i < records.size(); i++) {
                CSVRecord record = records.get(i);
                int rowNumber = i + 2; // +2 because index is 0-based and we skip header row

                try {
                    Map<String, String> rowData = new HashMap<>();
                    for (String header : headers) {
                        rowData.put(header, record.get(header));
                    }

                    // If canonical fields present, validate row data types & required fields
                    if (canonicalFields != null && !canonicalFields.isEmpty()) {
                        CsvValidationUtil.ValidationResult rowValidation = CsvValidationUtil.validateRow(rowData, fieldMap, headerMapping);
                        if (!rowValidation.isValid()) {
                            Map<String, String> normalized = LeadNormalizationUtil.normalizeRowValues(rowData, headerMapping);
                            invalidRows.add(new ParsedRow(rowNumber, rowData, normalized, rowValidation.getErrors()));
                            log.warn("Row {} failed validation: {}", rowNumber, rowValidation.getErrors());
                            continue;
                        }
                    }

                    Map<String, String> normalized = LeadNormalizationUtil.normalizeRowValues(rowData, headerMapping);

                    // Provide more detailed identifier validation errors (email/phone/aadhar format issues)
                    List<String> identifierIssues = new ArrayList<>();
                    boolean hadEmailValue = hasMappedNonEmptyValue(rowData, headerMapping, "email");
                    boolean hadPhoneValue = hasMappedNonEmptyValue(rowData, headerMapping, "phone_number");
                    boolean hadAadharValue = hasMappedNonEmptyValue(rowData, headerMapping, "aadhar_number");

                    if (hadEmailValue && !normalized.containsKey("email")) {
                        identifierIssues.add("Invalid email format");
                    }
                    if (hadPhoneValue && !normalized.containsKey("phone_number")) {
                        identifierIssues.add("Invalid phone number");
                    }
                    if (hadAadharValue && !normalized.containsKey("aadhar_number")) {
                        identifierIssues.add("Invalid aadhar number");
                    }

                    if (!LeadNormalizationUtil.validateIdentifiers(normalized)) {
                        List<String> errors = new ArrayList<>();
                        if (!identifierIssues.isEmpty()) {
                            errors.addAll(identifierIssues);
                        }
                        errors.add("At least one valid identifier (phone_number, email, or aadhar_number) is required");
                        invalidRows.add(new ParsedRow(rowNumber, rowData, normalized, errors));
                        log.warn("Row {} failed identifier validation: {}", rowNumber, errors);
                        continue;
                    }

                    validRows.add(new ParsedRow(rowNumber, rowData, normalized, Collections.emptyList()));
                } catch (Exception e) {
                    String msg = e.getMessage() != null ? e.getMessage() : "Failed to parse row";
                    invalidRows.add(new ParsedRow(rowNumber, Collections.emptyMap(), Collections.emptyMap(),
                            Collections.singletonList(msg)));
                    log.error("Row {} failed during CSV parsing: {}", rowNumber, msg, e);
                }
            }

            return new ParseResult(validRows.size() > 0, records.size(), validRows, invalidRows);
        } catch (IOException e) {
            return new ParseResult(false, 0, Collections.emptyList(),
                    Collections.singletonList(new ParsedRow(1, Collections.emptyMap(), Collections.emptyMap(),
                            Collections.singletonList(e.getMessage() != null ? e.getMessage() : "Failed to parse CSV file"))));
        }
    }

    private static boolean hasMappedNonEmptyValue(
            Map<String, String> rowData,
            Map<String, String> headerMapping,
            String canonicalKey
    ) {
        for (Map.Entry<String, String> entry : rowData.entrySet()) {
            String mapped = headerMapping.get(entry.getKey());
            if (!canonicalKey.equals(mapped)) continue;
            String v = entry.getValue();
            if (v != null && !v.trim().isEmpty()) return true;
        }
        return false;
    }
}
