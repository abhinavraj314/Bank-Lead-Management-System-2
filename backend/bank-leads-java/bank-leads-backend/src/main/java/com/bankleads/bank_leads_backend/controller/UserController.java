package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.request.CreateUserRequest;
import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.dto.response.UserResponse;
import com.bankleads.bank_leads_backend.model.User;
import com.bankleads.bank_leads_backend.service.UserService;
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

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        try {
            UserResponse created = userService.createUser(request);
            return ResponseUtil.success(created, "User created successfully", HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseUtil.error(e.getMessage(), HttpStatus.BAD_REQUEST);
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
}
