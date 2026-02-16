package com.bankleads.bank_leads_backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "leads")
@CompoundIndex(name = "lead_identifiers_index", def = "{'email': 1, 'phoneNumber': 1, 'aadharNumber': 1}")
@CompoundIndex(name = "lead_dashboard_index", def = "{'pId': 1, 'sourceId': 1, 'createdAt': -1}")
public class Lead {
    public enum EmploymentType {
        SALARIED,
        SELF_EMPLOYED,
        OTHER
    }
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    @Field("lead_id")                // aligns with existing Mongo unique index lead_id_1
    @JsonProperty("leadId")          // JSON property is camelCase for Spring Boot API
    @Builder.Default
    private String leadId = UUID.randomUUID().toString();
    
    private String name;
    
    @Indexed
    private String phoneNumber;
    
    @Indexed
    private String email;
    
    @Indexed
    private String aadharNumber;
    
    private String sourceId;
    private String pId;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    // Merge metadata
    @Builder.Default
    private List<MergeRecord> mergedFrom = new ArrayList<>();
    
    @Builder.Default
    private List<String> sourcesSeen = new ArrayList<>();
    
    @Builder.Default
    private List<String> productsSeen = new ArrayList<>();
    
    // AI scoring stub fields
    private Integer leadScore;
    private String scoreReason;

    // Optional fields for ML/mock financial modeling
    private Integer income;          // monthly income
    private Integer creditScore;     // expected 550-850
    private EmploymentType employmentType;
    private Integer loanAmount;
    private Boolean converted;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;

    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MergeRecord {
        private LocalDateTime timestamp;
        private String sourceId;
        private String pId;
        private Object data; // Can store raw row data
    }
}
