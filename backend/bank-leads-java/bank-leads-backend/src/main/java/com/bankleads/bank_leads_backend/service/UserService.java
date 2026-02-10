package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.dto.request.CreateUserRequest;
import com.bankleads.bank_leads_backend.dto.response.UserResponse;
import com.bankleads.bank_leads_backend.model.User;
import com.bankleads.bank_leads_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists: " + request.getUsername());
        }
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }
        User user = User.builder()
                .username(request.getUsername().trim())
                .email(normalizedEmail)
                .password(request.getPassword())
                .role(request.getRole())
                .build();
        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    public Page<UserResponse> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    public UserResponse getUserById(String id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    public UserResponse getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    public UserResponse getUserByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    public UserResponse updateUser(String id, String email, User.Role role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        if (email != null && !email.isBlank()) {
            String normalizedEmail = email.trim().toLowerCase();
            if (!normalizedEmail.equalsIgnoreCase(user.getEmail()) && userRepository.findByEmail(normalizedEmail).isPresent()) {
                throw new IllegalArgumentException("Email already exists: " + email);
            }
            user.setEmail(normalizedEmail);
        }
        if (role != null) {
            user.setRole(role);
        }
        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    public void deleteUser(String id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("User not found: " + id);
        }
        userRepository.deleteById(id);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
