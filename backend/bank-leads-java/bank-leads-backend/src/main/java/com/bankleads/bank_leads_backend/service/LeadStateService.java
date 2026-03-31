package com.bankleads.bank_leads_backend.service;

import com.bankleads.bank_leads_backend.model.Lead;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service for lead lifecycle state transitions and assignment validation.
 */
@Service
@RequiredArgsConstructor
public class LeadStateService {
    
    /**
     * Validates if a state transition is allowed for a sales user.
     * Admins can make any transition (caller should check admin role separately).
     * 
     * @param from Current status (null treated as NEW)
     * @param to Desired new status
     * @param isAdmin Whether the user is an admin
     * @return true if transition is allowed
     */
    public boolean isValidTransition(Lead.LeadStatus from, Lead.LeadStatus to, boolean isAdmin) {
        if (isAdmin) {
            return true; // Admins can make any transition
        }
        
        // Treat null as NEW for backward compatibility
        if (from == null) {
            from = Lead.LeadStatus.NEW;
        }
        
        // Sales users: allowed transitions
        return switch (from) {
            case NEW -> to == Lead.LeadStatus.IN_PROGRESS || to == Lead.LeadStatus.CLOSED;
            case IN_PROGRESS -> to == Lead.LeadStatus.QUALIFIED || to == Lead.LeadStatus.CLOSED;
            case QUALIFIED -> to == Lead.LeadStatus.CLOSED || to == Lead.LeadStatus.IN_PROGRESS;
            case CLOSED -> false; // Terminal state for sales users
        };
    }
    
    /**
     * Updates lead status with validation and audit timestamp.
     * 
     * @param lead Lead to update
     * @param newStatus New status
     * @param isAdmin Whether user is admin
     * @throws IllegalArgumentException if transition is invalid
     */
    public void updateStatus(Lead lead, Lead.LeadStatus newStatus, boolean isAdmin) {
        // Prefer the primary field `state` when present; fall back to `status` for older documents.
        Lead.LeadStatus currentStatus =
                lead.getState() != null ? lead.getState()
                        : (lead.getStatus() != null ? lead.getStatus() : Lead.LeadStatus.NEW);
        
        if (!isValidTransition(currentStatus, newStatus, isAdmin)) {
            throw new IllegalArgumentException(
                String.format("Invalid state transition from %s to %s", currentStatus, newStatus)
            );
        }
        
        // Update the correct Mongo field:
        // - If `state` exists, update `state` (avoid creating/updating a separate `status` field).
        // - If `state` is absent, update `status` for backward compatibility.
        if (lead.getState() != null) {
            lead.setState(newStatus);
            // Keep aliases in sync only if `status` already exists in the document.
            if (lead.getStatus() != null) {
                lead.setStatus(newStatus);
            }
        } else {
            lead.setStatus(newStatus);
        }
        lead.setStatusUpdatedAt(LocalDateTime.now());
    }
    
    /**
     * Checks if a user can update a lead's state.
     * Sales users can only update leads assigned to them.
     * 
     * @param lead Lead to check
     * @param userId Current user ID
     * @param isAdmin Whether user is admin
     * @return true if user can update
     */
    public boolean canUpdateState(Lead lead, String userId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        // Sales user: must be assigned to them
        return lead.getAssignedUserId() != null && lead.getAssignedUserId().equals(userId);
    }
    
    /**
     * Checks if a user can view a lead.
     * Sales users can only view leads assigned to them.
     * 
     * @param lead Lead to check
     * @param userId Current user ID
     * @param isAdmin Whether user is admin
     * @return true if user can view
     */
    public boolean canViewLead(Lead lead, String userId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        // Sales user: must be assigned to them
        return lead.getAssignedUserId() != null && lead.getAssignedUserId().equals(userId);
    }
}
