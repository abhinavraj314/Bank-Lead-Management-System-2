package com.bankleads.bank_leads_backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeadDTO {
    private String leadId;
    private String name;
    private String email;
    private String phoneNumber;
    private String aadharNumber;
    private String pId;
    private String productName;
    private String sourceId;
    private String sourceName;
    private LocalDateTime createdAt;
}
