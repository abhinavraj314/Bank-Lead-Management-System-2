package com.bankleads.bank_leads_backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateProductRequest {
    
    @JsonProperty("p_id")
    @NotBlank(message = "Product ID is required")
    @Size(min = 2, max = 20, message = "Product ID must be between 2 and 20 characters")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Product ID must contain only uppercase letters, numbers, and underscores")
    private String pId;
    
    @JsonProperty("p_name")
    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 100, message = "Product name must be between 2 and 100 characters")
    private String pName;
    
    /** Canonical fields used for lead deduplication: e.g. ["email", "phone_number", "aadhar_number"] */
    @JsonProperty("deduplication_fields")
    private List<String> deduplicationFields;
}
