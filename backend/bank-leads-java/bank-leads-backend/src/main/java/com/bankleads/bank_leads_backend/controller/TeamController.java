package com.bankleads.bank_leads_backend.controller;

import com.bankleads.bank_leads_backend.dto.response.ApiResponse;
import com.bankleads.bank_leads_backend.model.Team;
import com.bankleads.bank_leads_backend.repository.TeamRepository;
import com.bankleads.bank_leads_backend.repository.UserRepository;
import com.bankleads.bank_leads_backend.util.ResponseUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teams")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class TeamController {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Team>>> listTeams() {
        return ResponseUtil.success(teamRepository.findAll());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<ApiResponse<Team>> createTeam(@RequestBody Team team) {
        // Validate adminUserId is provided and exists
        if (team.getAdminUserId() == null || team.getAdminUserId().trim().isEmpty()) {
            return ResponseUtil.error("Team admin user ID is required", HttpStatus.BAD_REQUEST);
        }
        
        String adminUserId = team.getAdminUserId().trim();
        if (!userRepository.existsById(adminUserId)) {
            return ResponseUtil.error("Admin user not found: " + adminUserId, HttpStatus.BAD_REQUEST);
        }
        
        team.setId(null);
        team.setAdminUserId(adminUserId);
        if (team.getMemberUserIds() == null) {
            team.setMemberUserIds(List.of());
        }
        Team saved = teamRepository.save(team);
        return ResponseUtil.success(saved, "Team created", HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Team>> updateTeam(@PathVariable String id, @RequestBody Team body) {
        var team = teamRepository.findById(id);
        
        if (team.isEmpty()) {
            return ResponseUtil.error("Team not found: " + id, HttpStatus.NOT_FOUND);
        }
        
        Team existing = team.get();
        
        // Validate adminUserId if provided
        if (body.getAdminUserId() != null && !body.getAdminUserId().trim().isEmpty()) {
            String adminUserId = body.getAdminUserId().trim();
            if (!userRepository.existsById(adminUserId)) {
                return ResponseUtil.error("Admin user not found: " + adminUserId, HttpStatus.BAD_REQUEST);
            }
            existing.setAdminUserId(adminUserId);
        }
        
        if (body.getName() != null) {
            existing.setName(body.getName());
        }
        if (body.getMemberUserIds() != null) {
            existing.setMemberUserIds(body.getMemberUserIds());
        }
        return ResponseUtil.success(teamRepository.save(existing), "Team updated");
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteTeam(@PathVariable String id) {
        if (!teamRepository.existsById(id)) {
            return ResponseUtil.error("Team not found: " + id, HttpStatus.NOT_FOUND);
        }
        teamRepository.deleteById(id);
        return ResponseUtil.success((Object) java.util.Map.of("message", "Team deleted"));
    }
}
