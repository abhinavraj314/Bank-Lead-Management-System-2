package com.bankleads.bank_leads_backend.util;

import com.bankleads.bank_leads_backend.model.CanonicalField;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

public class CsvValidationUtil {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10,12}$");
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );
    
    public static class ValidationResult {
        private final boolean isValid;
        private final List<String> errors;
        
        public ValidationResult(boolean isValid, List<String> errors) {
            this.isValid = isValid;
            this.errors = errors != null ? errors : new ArrayList<>();
        }
        
        public boolean isValid() { return isValid; }
        public List<String> getErrors() { return errors; }
    }
    
    /**
     * Validates that the number of CSV columns matches the number of active canonical fields
     */
    public static ValidationResult validateFieldCount(
            List<String> csvHeaders, 
            List<CanonicalField> canonicalFields) {
        
        List<String> errors = new ArrayList<>();
        
        // Filter only active canonical fields
        List<CanonicalField> activeFields = canonicalFields.stream()
                .filter(field -> field.getIsActive() != null && field.getIsActive())
                .toList();
        
        int expectedFieldCount = activeFields.size();
        int actualFieldCount = csvHeaders != null ? csvHeaders.size() : 0;
        
        if (actualFieldCount != expectedFieldCount) {
            errors.add(String.format(
                    "Field count mismatch: Expected %d fields (based on active canonical fields), but CSV has %d columns",
                    expectedFieldCount, actualFieldCount));
            return new ValidationResult(false, errors);
        }
        
        return new ValidationResult(true, errors);
    }
    
    /**
     * Validates that CSV headers match canonical field names
     */
    public static ValidationResult validateHeaders(
            List<String> csvHeaders,
            List<CanonicalField> canonicalFields) {
        
        List<String> errors = new ArrayList<>();
        Map<String, CanonicalField> fieldMap = new HashMap<>();
        
        // Create map of normalized field names to canonical fields
        for (CanonicalField field : canonicalFields) {
            if (field.getIsActive() != null && field.getIsActive()) {
                String normalizedName = field.getFieldName().toLowerCase().trim();
                fieldMap.put(normalizedName, field);
            }
        }
        
        // Normalize CSV headers and check against canonical fields
        Set<String> foundFields = new HashSet<>();
        for (String csvHeader : csvHeaders) {
            String normalized = csvHeader.trim().toLowerCase().replaceAll("\\s+", "_");
            
            // Check direct match
            if (!fieldMap.containsKey(normalized)) {
                // Check if it maps to a canonical field via header mapping
                String mapped = LeadNormalizationUtil.normalizeHeaders(new String[]{csvHeader})
                        .getOrDefault(csvHeader, normalized);
                
                if (!fieldMap.containsKey(mapped)) {
                    errors.add(String.format(
                            "Header '%s' does not match any active canonical field", csvHeader));
                } else {
                    foundFields.add(mapped);
                }
            } else {
                foundFields.add(normalized);
            }
        }
        
        // Check if all required canonical fields are present
        for (CanonicalField field : canonicalFields) {
            if (field.getIsActive() != null && field.getIsActive()) {
                String normalizedName = field.getFieldName().toLowerCase().trim();
                if (!foundFields.contains(normalizedName)) {
                    // Check if it's mapped via header normalization
                    boolean found = false;
                    for (String csvHeader : csvHeaders) {
                        String mapped = LeadNormalizationUtil.normalizeHeaders(new String[]{csvHeader})
                                .getOrDefault(csvHeader, "");
                        if (mapped.equals(normalizedName)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        errors.add(String.format(
                                "Missing required canonical field: '%s'", field.getFieldName()));
                    }
                }
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * Validates data type of a single field value against its canonical field type
     */
    public static ValidationResult validateDataType(String value, CanonicalField field) {
        List<String> errors = new ArrayList<>();
        
        if (value == null || value.trim().isEmpty()) {
            if (field.getIsRequired() != null && field.getIsRequired()) {
                errors.add(String.format("Field '%s' is required but is empty", field.getFieldName()));
            }
            return new ValidationResult(errors.isEmpty(), errors);
        }
        
        String trimmedValue = value.trim();
        CanonicalField.FieldType fieldType = field.getFieldType();
        
        switch (fieldType) {
            case String:
                // String type accepts any non-empty value
                break;
                
            case Number:
                if (!isValidNumber(trimmedValue)) {
                    errors.add(String.format(
                            "Field '%s' expects Number type but got '%s'", 
                            field.getFieldName(), trimmedValue));
                }
                break;
                
            case Date:
                if (!isValidDate(trimmedValue)) {
                    errors.add(String.format(
                            "Field '%s' expects Date type but got '%s' (expected formats: yyyy-MM-dd, dd-MM-yyyy, MM/dd/yyyy, dd/MM/yyyy, yyyy/MM/dd)", 
                            field.getFieldName(), trimmedValue));
                }
                break;
                
            case Boolean:
                if (!isValidBoolean(trimmedValue)) {
                    errors.add(String.format(
                            "Field '%s' expects Boolean type but got '%s' (expected: true, false, yes, no, 1, 0)", 
                            field.getFieldName(), trimmedValue));
                }
                break;
                
            case Email:
                if (!isValidEmail(trimmedValue)) {
                    errors.add(String.format(
                            "Field '%s' expects Email type but got '%s'", 
                            field.getFieldName(), trimmedValue));
                }
                break;
                
            case Phone:
                if (!isValidPhone(trimmedValue)) {
                    errors.add(String.format(
                            "Field '%s' expects Phone type but got '%s' (expected: 10-12 digits)", 
                            field.getFieldName(), trimmedValue));
                }
                break;
                
            default:
                errors.add(String.format("Unknown field type: %s", fieldType));
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * Validates a row of data against all canonical fields
     */
    public static ValidationResult validateRow(
            Map<String, String> rowData,
            Map<String, CanonicalField> fieldMap,
            Map<String, String> headerMapping) {
        
        List<String> errors = new ArrayList<>();
        
        // Validate each field in the row
        for (Map.Entry<String, String> entry : rowData.entrySet()) {
            String originalHeader = entry.getKey();
            String mappedField = headerMapping.getOrDefault(originalHeader, 
                    originalHeader.toLowerCase().trim().replaceAll("\\s+", "_"));
            
            CanonicalField field = fieldMap.get(mappedField);
            if (field != null && field.getIsActive() != null && field.getIsActive()) {
                ValidationResult fieldValidation = validateDataType(entry.getValue(), field);
                if (!fieldValidation.isValid()) {
                    errors.addAll(fieldValidation.getErrors());
                }
            }
        }
        
        // Check for missing required fields
        for (Map.Entry<String, CanonicalField> fieldEntry : fieldMap.entrySet()) {
            CanonicalField field = fieldEntry.getValue();
            if (field.getIsActive() != null && field.getIsActive() && 
                field.getIsRequired() != null && field.getIsRequired()) {
                
                boolean found = false;
                for (Map.Entry<String, String> rowEntry : rowData.entrySet()) {
                    String mappedField = headerMapping.getOrDefault(rowEntry.getKey(),
                            rowEntry.getKey().toLowerCase().trim().replaceAll("\\s+", "_"));
                    if (mappedField.equals(fieldEntry.getKey())) {
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    errors.add(String.format("Required field '%s' is missing", field.getFieldName()));
                }
            }
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    // Helper methods for data type validation
    
    private static boolean isValidNumber(String value) {
        try {
            // Try parsing as double (handles integers and decimals)
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private static boolean isValidDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate.parse(value, formatter);
                return true;
            } catch (DateTimeParseException e) {
                // Try next formatter
            }
        }
        return false;
    }
    
    private static boolean isValidBoolean(String value) {
        String lower = value.toLowerCase().trim();
        return lower.equals("true") || lower.equals("false") ||
               lower.equals("yes") || lower.equals("no") ||
               lower.equals("1") || lower.equals("0") ||
               lower.equals("y") || lower.equals("n");
    }
    
    private static boolean isValidEmail(String value) {
        return EMAIL_PATTERN.matcher(value).matches();
    }
    
    private static boolean isValidPhone(String value) {
        // Remove non-digit characters for validation
        String digits = value.replaceAll("\\D", "");
        return PHONE_PATTERN.matcher(digits).matches();
    }
}
