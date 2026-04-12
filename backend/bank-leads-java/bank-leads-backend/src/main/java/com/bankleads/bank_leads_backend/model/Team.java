package com.bankleads.bank_leads_backend.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "teams")
public class Team {

    @Id
    private String id;

    private String name;

    /**
     * ID of the user who administers this team.
     * Required field - every team must have a designated admin.
     */
    @Indexed
    private String adminUserId;

    @Builder.Default
    private List<String> memberUserIds = new ArrayList<>();

    /** Next index for round-robin within {@link #memberUserIds} */
    @Builder.Default
    private int roundRobinIndex = 0;
}
