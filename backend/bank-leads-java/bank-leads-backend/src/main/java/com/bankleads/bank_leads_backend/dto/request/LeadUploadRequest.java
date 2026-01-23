package com.bankleads.bank_leads_backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LeadUploadRequest {
    @NotBlank(message = "Product ID is required")
    private String pId;
    
    @NotBlank(message = "Source ID is required")
    private String sourceId;
}
