package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.dto.request.CreateUserRequest;
import com.bankleads.bank_leads_backend.dto.response.UserResponse;
import com.bankleads.bank_leads_backend.model.User;
import com.bankleads.bank_leads_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Generate next user code in format USR_NNNN (e.g., USR_0001, USR_0002)
     */
    private String generateUserCode() {
        var lastUser = userRepository.findTopByOrderByUserCodeDesc();
        int nextNumber = 1;
        
        if (lastUser.isPresent() && lastUser.get().getUserCode() != null) {
            String lastCode = lastUser.get().getUserCode();
            try {
                // Extract number from USR_NNNN format
                String numPart = lastCode.replaceAll("[^0-9]", "");
                nextNumber = Integer.parseInt(numPart) + 1;
            } catch (NumberFormatException e) {
                nextNumber = 1;
            }
        }
        
        return String.format("USR_%04d", nextNumber);
    }

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
                // Hash password before saving
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .userCode(generateUserCode())
                .accountStatus(User.AccountStatus.ACTIVE)
                .build();
        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    public Page<UserResponse> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toResponse);
    }

    /**
     * Get all active users as a list (for dropdowns, team selection, etc.)
     * Does not include INVITED users.
     */
    public List<UserResponse> getAllActiveUsers() {
        return userRepository.findAll()
                .stream()
                .filter(u -> u.getAccountStatus() == User.AccountStatus.ACTIVE)
                .map(this::toResponse)
                .toList();
    }

    public UserResponse getUserById(String id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    public UserResponse getUserByUsername(String username) {
        User user = findUserEntityByUsername(username);
        if (user.getAccountStatus() != User.AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("User not found: " + username);
        }
        return toResponse(user);
    }

    public UserResponse getUserByEmail(String email) {
        User user = findUserEntityByEmail(email);
        if (user.getAccountStatus() != User.AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("User not found: " + email);
        }
        return toResponse(user);
    }

    // Expose entity-level finders for login logic
    public User findUserEntityByUsername(String username) {
        return userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }

    public User findUserEntityByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
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

    public UserResponse toResponsePublic(User user) {
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .userCode(user.getUserCode())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .accountStatus(user.getAccountStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
