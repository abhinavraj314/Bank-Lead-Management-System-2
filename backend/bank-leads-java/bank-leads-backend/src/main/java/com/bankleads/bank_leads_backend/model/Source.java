package com.bankleads.bank_leads_backend.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;

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

    /**
     * Metadata columns describing what data this source provides.
     * Optional for backward compatibility – existing documents may not have this field.
     */
    @Field("columns")
    @JsonProperty("columns")
    private List<String> columns;
    
    @Field("created_at")
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @Field("updated_at")
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
}
