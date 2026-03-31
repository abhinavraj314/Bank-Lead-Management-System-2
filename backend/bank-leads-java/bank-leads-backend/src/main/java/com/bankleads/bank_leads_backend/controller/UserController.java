package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.request.CreateUserRequest;
import com.bankleads.bank_leads_backend.dto.request.AcceptInvitationRequest;
import com.bankleads.bank_leads_backend.dto.request.InviteUserRequest;
import com.bankleads.bank_leads_backend.dto.request.LoginRequest;
import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.dto.response.UserResponse;
import com.bankleads.bank_leads_backend.model.User;
import com.bankleads.bank_leads_backend.service.UserService;
import com.bankleads.bank_leads_backend.service.UserInvitationService;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserInvitationService userInvitationService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        try {
            UserResponse created = userService.createUser(request);
            return ResponseUtil.success(created, "User created successfully", HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Login endpoint: validates username/email + password.
     * Returns user details on success (frontend continues to use user.id as token).
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> login(@Valid @RequestBody LoginRequest request) {
        String identifier = request.getIdentifier().trim();
        String rawPassword = request.getPassword();

        try {
            User user;
            // Decide whether identifier looks like an email
            if (identifier.contains("@")) {
                user = userService.findUserEntityByEmail(identifier);
            } else {
                user = userService.findUserEntityByUsername(identifier);
            }

            // Basic account status check (INVITED users cannot log in)
            if (user.getAccountStatus() != User.AccountStatus.ACTIVE) {
                return ResponseUtil.error("Invalid username/email or password", HttpStatus.UNAUTHORIZED);
            }

            String storedPassword = user.getPassword();
            if (storedPassword == null || storedPassword.isBlank()) {
                return ResponseUtil.error("Invalid username/email or password", HttpStatus.UNAUTHORIZED);
            }

            boolean matches;
            if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
                matches = passwordEncoder.matches(rawPassword, storedPassword);
            } else {
                // Legacy plain-text support (for any existing accounts created before hashing)
                matches = rawPassword.equals(storedPassword);
            }

            if (!matches) {
                return ResponseUtil.error("Invalid username/email or password", HttpStatus.UNAUTHORIZED);
            }

            UserResponse response = userService.toResponsePublic(user);
            return ResponseUtil.success(response, "Login successful");
        } catch (IllegalArgumentException ex) {
            return ResponseUtil.error("Invalid username/email or password", HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String order) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page - 1),
                Math.min(100, Math.max(1, limit)),
                Sort.by("desc".equalsIgnoreCase(order) ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy));
        Page<UserResponse> users = userService.getUsers(pageable);
        return ResponseUtil.success(users, "Users retrieved successfully");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable String id) {
        try {
            UserResponse user = userService.getUserById(id);
            return ResponseUtil.success(user);
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByUsername(@PathVariable String username) {
        try {
            UserResponse user = userService.getUserByUsername(username);
            return ResponseUtil.success(user);
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByEmail(@PathVariable String email) {
        try {
            UserResponse user = userService.getUserByEmail(email);
            return ResponseUtil.success(user);
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable String id,
            @RequestBody Map<String, Object> updates) {
        try {
            String email = updates.containsKey("email") ? (String) updates.get("email") : null;
            User.Role role = null;
            if (updates.containsKey("role")) {
                String roleStr = (String) updates.get("role");
                if (roleStr != null) {
                    role = User.Role.valueOf(roleStr.toUpperCase());
                }
            }
            UserResponse updated = userService.updateUser(id, email, role);
            return ResponseUtil.success(updated, "User updated successfully");
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteUser(@PathVariable String id) {
        try {
            userService.deleteUser(id);
            return ResponseUtil.success(Map.of("message", "User deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error(e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }

    // ===================== Invitations =====================

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/invitations")
    public ResponseEntity<ApiResponse<Map<String, String>>> inviteUser(
            @Valid @RequestBody InviteUserRequest request) {
        try {
            String rawToken = userInvitationService.createInvitation(request);
            // Frontend can construct the link, but return token for convenience.
            return ResponseUtil.success(
                    Map.of("token", rawToken),
                    "Invitation created successfully",
                    HttpStatus.CREATED
            );
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<ApiResponse<UserResponse>> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request) {
        try {
            User accepted = userInvitationService.acceptInvitation(request);
            UserResponse response = UserResponse.builder()
                    .id(accepted.getId())
                    .username(accepted.getUsername())
                    .email(accepted.getEmail())
                    .role(accepted.getRole())
                    .accountStatus(accepted.getAccountStatus())
                    .createdAt(accepted.getCreatedAt())
                    .updatedAt(accepted.getUpdatedAt())
                    .build();
            return ResponseUtil.success(response, "Invitation accepted successfully");
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
