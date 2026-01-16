package com.bankleads.bank_leads_backend.repository;

import com.bankleads.bank_leads_backend.model.Lead;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeadRepository extends MongoRepository<Lead, String> {
    Optional<Lead> findByLeadId(String leadId);
    boolean existsByLeadId(String leadId);
    
    List<Lead> findByEmail(String email);
    List<Lead> findByPhoneNumber(String phoneNumber);
    List<Lead> findByAadharNumber(String aadharNumber);
    
    Page<Lead> findByPId(String pId, Pageable pageable);
    Page<Lead> findBySourceId(String sourceId, Pageable pageable);
    long countBySourceId(String sourceId);
}
