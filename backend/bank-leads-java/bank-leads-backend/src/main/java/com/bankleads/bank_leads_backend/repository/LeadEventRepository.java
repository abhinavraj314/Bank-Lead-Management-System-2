package com.bankleads.bank_leads_backend.repository;

import com.bankleads.bank_leads_backend.model.LeadEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LeadEventRepository extends MongoRepository<LeadEvent, String> {
    Page<LeadEvent> findByLeadIdOrderByAtDesc(String leadId, Pageable pageable);
}
