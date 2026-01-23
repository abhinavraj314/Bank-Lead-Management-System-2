package com.bankleads.bank_leads_backend.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class LeadNormalizationUtil {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    
    private static final Map<String, String> HEADER_MAP = new HashMap<>();
    
    static {
        // Name variations
        HEADER_MAP.put("name", "name");
        HEADER_MAP.put("full_name", "name");
        HEADER_MAP.put("fullname", "name");
        HEADER_MAP.put("customer_name", "name");
        HEADER_MAP.put("customername", "name");
        HEADER_MAP.put("client_name", "name");
        
        // Phone variations
        HEADER_MAP.put("phone", "phone_number");
        HEADER_MAP.put("phone_number", "phone_number");
        HEADER_MAP.put("phonenumber", "phone_number");
        HEADER_MAP.put("mobile", "phone_number");
        HEADER_MAP.put("mobile_number", "phone_number");
        HEADER_MAP.put("contact", "phone_number");
        HEADER_MAP.put("contact_number", "phone_number");
        
        // Email variations
        HEADER_MAP.put("email", "email");
        HEADER_MAP.put("email_id", "email");
        HEADER_MAP.put("emailid", "email");
        HEADER_MAP.put("mail", "email");
        HEADER_MAP.put("e_mail", "email");
        HEADER_MAP.put("email_address", "email");
        
        // Aadhar variations
        HEADER_MAP.put("aadhar", "aadhar_number");
        HEADER_MAP.put("aadhaar", "aadhar_number");
        HEADER_MAP.put("aadhar_number", "aadhar_number");
        HEADER_MAP.put("aadhaar_number", "aadhar_number");
        HEADER_MAP.put("aadhar_no", "aadhar_number");
    }
    
    public static Map<String, String> normalizeHeaders(String[] headers) {
        Map<String, String> mapping = new HashMap<>();
        for (String header : headers) {
            String normalized = header.trim().toLowerCase().replaceAll("\\s+", "_");
            String mapped = HEADER_MAP.get(normalized);
            if (mapped != null) {
                mapping.put(header, mapped);
            }
        }
        return mapping;
    }
    
    public static String normalizePhone(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        
        // Handle Indian country code (+91)
        if (digits.length() > 10) {
            if (digits.startsWith("91") && digits.length() == 12) {
                return digits.substring(2); // Remove 91 prefix
            }
            return digits.substring(Math.max(0, digits.length() - 10)); // Take last 10 digits
        }
        
        if (digits.length() < 10) {
            return null;
        }
        
        return digits;
    }
    
    public static String normalizeEmail(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        String email = value.trim().toLowerCase();
        if (email.isEmpty()) {
            return null;
        }
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return null;
        }
        
        return email;
    }
    
    public static String normalizeAadhar(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        String digits = value.replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() != 12) {
            return null;
        }
        
        return digits;
    }
    
    public static Map<String, String> normalizeRowValues(Map<String, String> row, Map<String, String> headerMapping) {
        Map<String, String> result = new HashMap<>();
        
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String originalKey = entry.getKey();
            String mappedKey = headerMapping.get(originalKey);
            
            if (mappedKey == null) {
                continue;
            }
            
            String rawValue = entry.getValue();
            if (rawValue == null || rawValue.trim().isEmpty()) {
                continue;
            }
            
            switch (mappedKey) {
                case "phone_number":
                    String phone = normalizePhone(rawValue);
                    if (phone != null) {
                        result.put("phone_number", phone);
                    }
                    break;
                case "email":
                    String email = normalizeEmail(rawValue);
                    if (email != null) {
                        result.put("email", email);
                    }
                    break;
                case "aadhar_number":
                    String aadhar = normalizeAadhar(rawValue);
                    if (aadhar != null) {
                        result.put("aadhar_number", aadhar);
                    }
                    break;
                case "name":
                    String name = rawValue.trim();
                    if (!name.isEmpty()) {
                        result.put("name", name);
                    }
                    break;
            }
        }
        
        return result;
    }
    
    public static boolean validateIdentifiers(Map<String, String> normalized) {
        return (normalized.containsKey("phone_number") && normalized.get("phone_number") != null) ||
               (normalized.containsKey("email") && normalized.get("email") != null) ||
               (normalized.containsKey("aadhar_number") && normalized.get("aadhar_number") != null);
    }
}
