package com.bankleads.bank_leads_backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    
    @Id
    @JsonProperty("id")
    private String id;
    
    @Indexed(unique = true)
    @Field("p_id")
    @JsonProperty("pId")
    private String pId;
    
    @Field("p_name")
    @JsonProperty("pName")
    private String pName;
    
    /**
     * Canonical field names used for lead deduplication for this product.
     * Allowed values: "email", "phone_number", "aadhar_number".
     * When null or empty, defaults to all three.
     */
    @Field("deduplication_fields")
    @JsonProperty("deduplicationFields")
    @Builder.Default
    private List<String> deduplicationFields = new ArrayList<>();
    
    @Field("created_at")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @Field("updated_at")
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
}
