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

@Document(collection = "sources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Source {
    
    @Id
    @JsonProperty("id")
    private String id;
    
    @Indexed(unique = true)
    @Field("s_id")
    @JsonProperty("sId")
    private String sId;
    
    @Field("s_name")
    @JsonProperty("sName")
    private String sName;
    
    @Field("p_id")
    @JsonProperty("pId")
    private String pId;
    
    @Field("created_at")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @Field("updated_at")
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
}
