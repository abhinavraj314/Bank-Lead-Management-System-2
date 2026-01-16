package com.bankleads.bank_leads_backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateSourceRequest {
    
    @JsonProperty("s_id")
    @NotBlank(message = "Source ID is required")
    @Size(min = 2, max = 20, message = "Source ID must be between 2 and 20 characters")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Source ID must contain only uppercase letters, numbers, and underscores")
    private String sId;
    
    @JsonProperty("s_name")
    @NotBlank(message = "Source name is required")
    @Size(min = 2, max = 100, message = "Source name must be between 2 and 100 characters")
    private String sName;
    
    @JsonProperty("p_id")
    @NotBlank(message = "Product ID is required")
    private String pId;
}
