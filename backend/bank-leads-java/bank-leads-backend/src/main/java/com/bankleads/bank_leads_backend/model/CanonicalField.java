package com.bankleads.bank_leads_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "canonical_fields")
public class CanonicalField {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String fieldName;
    
    private String displayName;
    
    private FieldType fieldType;
    
    @Indexed
    private Boolean isActive;
    
    private Boolean isRequired;
    
    private String version;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    public enum FieldType {
        String, Number, Date, Boolean, Email, Phone
    }
}
