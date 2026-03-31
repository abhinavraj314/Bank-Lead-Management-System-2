package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.dto.request.AcceptInvitationRequest;
import com.bankleads.bank_leads_backend.dto.request.InviteUserRequest;
import com.bankleads.bank_leads_backend.model.User;
import com.bankleads.bank_leads_backend.model.UserInvitation;
import com.bankleads.bank_leads_backend.repository.UserInvitationRepository;
import com.bankleads.bank_leads_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserInvitationService {

    private static final Logger log = LoggerFactory.getLogger(UserInvitationService.class);

    private final UserRepository userRepository;
    private final UserInvitationRepository invitationRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend.base-url:http://localhost:4200}")
    private String frontendBaseUrl;

    @Value("${MAIL_FROM:}")
    private String mailFrom;

    @Value("${spring.mail.host:}")
    private String mailHost;

    public UserInvitationService(UserRepository userRepository,
                                 UserInvitationRepository invitationRepository,
                                 Optional<JavaMailSender> mailSender,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.invitationRepository = invitationRepository;
        this.mailSender = mailSender.orElse(null);
        this.passwordEncoder = passwordEncoder;
    }

    public String createInvitation(InviteUserRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User.Role role = request.getRole();

        // Ensure a placeholder invited user exists so it shows in the Users tab (Option B)
        User placeholder = userRepository.findByEmail(email).orElse(null);
        if (placeholder == null) {
            placeholder = User.builder()
                    .email(email)
                    .username(generateUniquePlaceholderUsername())
                    .password(null)
                    .role(role)
                    .accountStatus(User.AccountStatus.INVITED)
                    .build();
            placeholder = userRepository.save(placeholder);
        } else {
            // If already ACTIVE, we should block invitation to avoid duplicate accounts.
            if (placeholder.getAccountStatus() == User.AccountStatus.ACTIVE) {
                throw new IllegalArgumentException("Email already exists: " + request.getEmail());
            }
            placeholder.setRole(role);
            placeholder.setAccountStatus(User.AccountStatus.INVITED);
            userRepository.save(placeholder);
        }

        // Create (or replace) invitation token
        String rawToken = generateRawToken();
        String tokenHash = sha256Hex(rawToken);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusDays(7);

        // If there is an unconsumed invitation for the email, mark it consumed.
        invitationRepository.findUnconsumedByEmail(email).ifPresent(inv -> {
            inv.setConsumedAt(now);
            invitationRepository.save(inv);
        });

        UserInvitation invitation = UserInvitation.builder()
                .email(email)
                .role(role)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .consumedAt(null)
                .build();
        invitationRepository.save(invitation);

        String inviteLink = frontendBaseUrl + "/auth/accept-invite?token=" + rawToken;

        // Try to send email if SMTP is configured; otherwise return link for manual copy/testing.
        if (mailSender != null && mailHost != null && !mailHost.isBlank()) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                if (mailFrom != null && !mailFrom.isBlank()) {
                    message.setFrom(mailFrom);
                }
                message.setTo(email);
                message.setSubject("You're invited to Bank Lead Management System");
                message.setText("Hello,\n\nYou've been invited. Accept your invitation using:\n" + inviteLink + "\n\nThis invitation expires in 7 days.");
                mailSender.send(message);
            } catch (Exception ignored) {
                // Don’t break the invitation flow if email sending fails; UI can still use inviteLink.
                // But log the error so we can debug SMTP config issues.
                log.warn("Failed to send invitation email to '{}': {}", email, ignored.getMessage());
            }
        }

        // Return the raw token (frontend can construct link), but also enough info for debugging.
        return rawToken;
    }

    public User acceptInvitation(AcceptInvitationRequest request) {
        String rawToken = request.getToken();
        String tokenHash = sha256Hex(rawToken);

        UserInvitation invitation = invitationRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired invitation token"));

        LocalDateTime now = LocalDateTime.now();
        if (invitation.getConsumedAt() != null || invitation.getExpiresAt() == null || invitation.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("Invalid or expired invitation token");
        }

        String email = invitation.getEmail();
        User placeholder = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invitation already used or user not found"));

        if (placeholder.getAccountStatus() != User.AccountStatus.INVITED) {
            throw new IllegalArgumentException("Invitation already accepted");
        }

        // Validate username uniqueness
        String desiredUsername = request.getUsername().trim();
        if (!desiredUsername.isBlank() && userRepository.existsByUsername(desiredUsername) &&
                (placeholder.getUsername() == null || !placeholder.getUsername().equalsIgnoreCase(desiredUsername))) {
            throw new IllegalArgumentException("Username already exists: " + desiredUsername);
        }

        placeholder.setUsername(desiredUsername);
        // Hash password before activating the account
        placeholder.setPassword(passwordEncoder.encode(request.getPassword()));
        placeholder.setAccountStatus(User.AccountStatus.ACTIVE);
        placeholder.setRole(invitation.getRole());
        userRepository.save(placeholder);

        invitation.setConsumedAt(now);
        invitationRepository.save(invitation);

        return placeholder;
    }

    private String generateUniquePlaceholderUsername() {
        for (int i = 0; i < 10; i++) {
            String candidate = "INV_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            if (!userRepository.existsByUsername(candidate)) return candidate;
        }
        return "INV_" + UUID.randomUUID().toString().toUpperCase().replace("-", "").substring(0, 8);
    }

    private String generateRawToken() {
        // Long enough for brute-force resistance.
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash invitation token", e);
        }
    }
}

