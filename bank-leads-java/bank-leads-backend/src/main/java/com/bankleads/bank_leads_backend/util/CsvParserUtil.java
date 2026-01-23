package com.bankleads.bank_leads_backend.util;

import com.bankleads.bank_leads_backend.model.CanonicalField;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CsvParserUtil {
    
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
        private final Map<String, String> data;
        private final List<String> errors;
        
        public ParsedRow(int row, Map<String, String> data, List<String> errors) {
            this.row = row;
            this.data = data;
            this.errors = errors;
        }
        
        public int getRow() { return row; }
        public Map<String, String> getData() { return data; }
        public List<String> getErrors() { return errors; }
    }
    
    /**
     * Parse CSV without validation (backward compatibility)
     */
    public static ParseResult parseCSV(byte[] fileBuffer) {
        return parseCSV(fileBuffer, null);
    }
    
    /**
     * Parse CSV with canonical field validation
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
                        Collections.singletonList(new ParsedRow(1, Collections.emptyMap(),
                                Collections.singletonList("CSV file is empty or has no data rows"))));
            }
            
            List<String> headers = parser.getHeaderNames();
            Map<String, String> headerMapping = LeadNormalizationUtil.normalizeHeaders(headers.toArray(new String[0]));
            
            // Validate field count and headers if canonical fields are provided
            if (canonicalFields != null && !canonicalFields.isEmpty()) {
                // Validate field count
                CsvValidationUtil.ValidationResult countValidation = 
                        CsvValidationUtil.validateFieldCount(headers, canonicalFields);
                if (!countValidation.isValid()) {
                    return new ParseResult(false, 0, Collections.emptyList(),
                            Collections.singletonList(new ParsedRow(1, Collections.emptyMap(),
                                    countValidation.getErrors())));
                }
                
                // Validate headers
                CsvValidationUtil.ValidationResult headerValidation = 
                        CsvValidationUtil.validateHeaders(headers, canonicalFields);
                if (!headerValidation.isValid()) {
                    return new ParseResult(false, 0, Collections.emptyList(),
                            Collections.singletonList(new ParsedRow(1, Collections.emptyMap(),
                                    headerValidation.getErrors())));
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
                        // CSVRecord.get() returns empty string if value is missing, never null
                        String value = record.get(header);
                        rowData.put(header, value);
                    }
                    
                    // Validate data types if canonical fields are provided
                    if (canonicalFields != null && !canonicalFields.isEmpty()) {
                        CsvValidationUtil.ValidationResult rowValidation = 
                                CsvValidationUtil.validateRow(rowData, fieldMap, headerMapping);
                        if (!rowValidation.isValid()) {
                            invalidRows.add(new ParsedRow(rowNumber, rowData, rowValidation.getErrors()));
                            continue;
                        }
                    }
                    
                    Map<String, String> normalized = LeadNormalizationUtil.normalizeRowValues(rowData, headerMapping);
                    
                    if (!LeadNormalizationUtil.validateIdentifiers(normalized)) {
                        invalidRows.add(new ParsedRow(rowNumber, normalized,
                                Collections.singletonList("At least one identifier (phone_number, email, or aadhar_number) is required")));
                        continue;
                    }
                    
                    validRows.add(new ParsedRow(rowNumber, normalized, Collections.emptyList()));
                } catch (Exception e) {
                    invalidRows.add(new ParsedRow(rowNumber, Collections.emptyMap(),
                            Collections.singletonList(e.getMessage() != null ? e.getMessage() : "Failed to parse row")));
                }
            }
            
            return new ParseResult(validRows.size() > 0, records.size(), validRows, invalidRows);
        } catch (IOException e) {
            return new ParseResult(false, 0, Collections.emptyList(),
                    Collections.singletonList(new ParsedRow(1, Collections.emptyMap(),
                            Collections.singletonList(e.getMessage() != null ? e.getMessage() : "Failed to parse CSV file"))));
        }
    }
}
