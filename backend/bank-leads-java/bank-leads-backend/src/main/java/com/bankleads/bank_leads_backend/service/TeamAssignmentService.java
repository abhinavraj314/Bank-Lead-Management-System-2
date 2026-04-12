package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.AssignmentRule;
import com.bankleads.bank_leads_backend.model.Lead;
import com.bankleads.bank_leads_backend.model.Team;
import com.bankleads.bank_leads_backend.model.User;
import com.bankleads.bank_leads_backend.repository.AssignmentRuleRepository;
import com.bankleads.bank_leads_backend.repository.TeamRepository;
import com.bankleads.bank_leads_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamAssignmentService {

    private final AssignmentRuleRepository assignmentRuleRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    /**
     * Auto-assign a newly created lead when a matching rule exists. Mutates and saves {@code lead} and may save {@link Team}.
     */
    public void assignIfApplicable(Lead lead) {
        if (lead == null || lead.getAssignedUserId() != null) {
            return;
        }
        List<AssignmentRule> rules = assignmentRuleRepository.findAllByOrderByPriorityAsc();
        for (AssignmentRule rule : rules) {
            if (!matches(rule, lead)) {
                continue;
            }
            Team team = teamRepository.findById(rule.getTeamId()).orElse(null);
            if (team == null || team.getMemberUserIds() == null || team.getMemberUserIds().isEmpty()) {
                lead.setTeamId(team != null ? team.getId() : rule.getTeamId());
                return;
            }
            List<String> members = team.getMemberUserIds();
            int idx = Math.floorMod(team.getRoundRobinIndex(), members.size());
            String userId = members.get(idx);
            User user = userRepository.findById(userId).orElse(null);
            if (user == null) {
                continue;
            }
            lead.setTeamId(team.getId());
            lead.setAssignedUserId(userId);
            lead.setAssignedUserName(user.getUsername() != null ? user.getUsername() : user.getEmail());
            lead.setAssignedAt(LocalDateTime.now());
            team.setRoundRobinIndex(idx + 1);
            teamRepository.save(team);
            return;
        }
    }

    private static boolean matches(AssignmentRule rule, Lead lead) {
        String p = lead.getPId() != null ? lead.getPId().toUpperCase() : null;
        String s = lead.getSourceId() != null ? lead.getSourceId().toUpperCase() : null;
        if (rule.getProductId() != null && !rule.getProductId().isBlank()) {
            if (p == null || !p.equalsIgnoreCase(rule.getProductId().trim())) {
                return false;
            }
        }
        if (rule.getSourceId() != null && !rule.getSourceId().isBlank()) {
            if (s == null || !s.equalsIgnoreCase(rule.getSourceId().trim())) {
                return false;
            }
        }
        return true;
    }
}
