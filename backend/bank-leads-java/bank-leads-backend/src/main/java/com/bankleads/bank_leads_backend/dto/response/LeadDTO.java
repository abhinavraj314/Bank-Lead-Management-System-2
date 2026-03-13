package com.bankleads.bank_leads_backend.dto.response;

import com.bankleads.bank_leads_backend.model.Lead;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("phNo")
    private String phoneNumber;
    private String aadharNumber;
    private String pId;
    private String productName;
    private String sourceId;
    private String sourceName;
    private LocalDateTime createdAt;
    private Integer income;
    private Integer creditScore;
    private Lead.EmploymentType employmentType;
    private Integer loanAmount;
    private Boolean converted;
    private Double leadScore;
    private String scoreReason;
    private Lead.LeadStatus status;
    private Lead.LeadStatus state;  // Primary state field
    private String assignedUserId;
    private String assignedUserName;
    private LocalDateTime statusUpdatedAt;
    private LocalDateTime assignedAt;
}
