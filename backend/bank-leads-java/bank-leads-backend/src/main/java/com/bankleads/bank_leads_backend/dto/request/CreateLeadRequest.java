package com.bankleads.bank_leads_backend.dto.request;

import com.bankleads.bank_leads_backend.model.Lead;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateLeadRequest {
    @JsonProperty("name")
    @Size(max = 200, message = "Name must be less than 200 characters")
    private String name;
    
    @JsonProperty("phoneNumber")
    private String phoneNumber;
    
    @JsonProperty("email")
    @Email(message = "Invalid email format")
    private String email;
    
    @JsonProperty("aadharNumber")
    private String aadharNumber;
    
    @JsonProperty("sourceId")
    @NotBlank(message = "Source is required")
    private String sourceId;
    
    @JsonProperty("pId")
    @NotBlank(message = "Product is required")
    private String pId;

    @JsonProperty("income")
    @Min(value = 0, message = "Income must be non-negative")
    private Integer income;

    @JsonProperty("creditScore")
    @Min(value = 550, message = "Credit score must be between 550 and 850")
    @Max(value = 850, message = "Credit score must be between 550 and 850")
    private Integer creditScore;

    @JsonProperty("employmentType")
    private Lead.EmploymentType employmentType;

    @JsonProperty("loanAmount")
    @Min(value = 0, message = "Loan amount must be non-negative")
    private Integer loanAmount;

    @JsonProperty("converted")
    private Boolean converted;
}
