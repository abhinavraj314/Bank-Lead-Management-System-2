package com.bankleads.bank_leads_backend.dto.response;

import com.bankleads.bank_leads_backend.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private String id;
    private String username;
    private String email;
    private User.Role role;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
