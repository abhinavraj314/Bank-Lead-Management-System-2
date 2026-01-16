package com.bankleads.bank_leads_backend.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

public class ExcelParserUtil {
    
    /**
     * Parse Excel file (reuses same ParseResult/ParsedRow from CsvParserUtil)
     */
    public static CsvParserUtil.ParseResult parseExcel(byte[] fileBuffer) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(fileBuffer);
             Workbook workbook = new XSSFWorkbook(bis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            
            if (sheet.getPhysicalNumberOfRows() <= 1) {
                return new CsvParserUtil.ParseResult(false, 0, Collections.emptyList(),
                        Collections.singletonList(new CsvParserUtil.ParsedRow(1, 
                            Collections.emptyMap(),
                            Collections.singletonList("Excel file is empty or has no data rows"))));
            }
            
            // Get headers from first row
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(getCellValue(cell));
            }
            
            Map<String, String> headerMapping = LeadNormalizationUtil.normalizeHeaders(
                headers.toArray(new String[0])
            );
            
            List<CsvParserUtil.ParsedRow> validRows = new ArrayList<>();
            List<CsvParserUtil.ParsedRow> invalidRows = new ArrayList<>();
            
            // Process data rows (skip header row)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                int rowNumber = i + 1; // Excel rows are 1-indexed
                
                try {
                    Map<String, String> rowData = new HashMap<>();
                    for (int j = 0; j < headers.size(); j++) {
                        Cell cell = row.getCell(j);
                        String header = headers.get(j);
                        String value = cell != null ? getCellValue(cell) : "";
                        rowData.put(header, value);
                    }
                    
                    Map<String, String> normalized = LeadNormalizationUtil.normalizeRowValues(
                        rowData, headerMapping
                    );
                    
                    if (!LeadNormalizationUtil.validateIdentifiers(normalized)) {
                        invalidRows.add(new CsvParserUtil.ParsedRow(rowNumber, normalized,
                                Collections.singletonList("At least one identifier (phone_number, email, or aadhar_number) is required")));
                        continue;
                    }
                    
                    validRows.add(new CsvParserUtil.ParsedRow(rowNumber, normalized, Collections.emptyList()));
                } catch (Exception e) {
                    invalidRows.add(new CsvParserUtil.ParsedRow(rowNumber, Collections.emptyMap(),
                            Collections.singletonList(e.getMessage() != null ? e.getMessage() : "Failed to parse row")));
                }
            }
            
            return new CsvParserUtil.ParseResult(validRows.size() > 0, 
                sheet.getLastRowNum(), validRows, invalidRows);
                
        } catch (IOException e) {
            return new CsvParserUtil.ParseResult(false, 0, Collections.emptyList(),
                    Collections.singletonList(new CsvParserUtil.ParsedRow(1, 
                        Collections.emptyMap(),
                        Collections.singletonList(e.getMessage() != null ? e.getMessage() : "Failed to parse Excel file"))));
        }
    }
    
    /**
     * Get cell value as string
     */
    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                // Format numeric as string (remove decimal if integer)
                double numValue = cell.getNumericCellValue();
                if (numValue == (long) numValue) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return "";
            default:
                return "";
        }
    }
}
