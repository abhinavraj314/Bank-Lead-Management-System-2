package com.bankleads.bank_leads_backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateLeadRequest {
    @Size(max = 200, message = "Name must be less than 200 characters")
    private String name;
    
    @Pattern(regexp = "^\\d{10}$", message = "Phone number must be exactly 10 digits")
    private String phoneNumber;
    
    @Email(message = "Invalid email format")
    private String email;
    
    @Pattern(regexp = "^\\d{12}$", message = "Aadhar number must be exactly 12 digits")
    private String aadharNumber;
    
    private String sourceId;
    private String pId;
}
