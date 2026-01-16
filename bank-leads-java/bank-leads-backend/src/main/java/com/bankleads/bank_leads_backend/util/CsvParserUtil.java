package com.bankleads.bank_leads_backend.util;

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
    
    public static ParseResult parseCSV(byte[] fileBuffer) {
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
