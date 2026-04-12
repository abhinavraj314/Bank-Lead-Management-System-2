package com.bankleads.bank_leads_backend.repository;

import com.bankleads.bank_leads_backend.model.AssignmentRule;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AssignmentRuleRepository extends MongoRepository<AssignmentRule, String> {
    List<AssignmentRule> findAllByOrderByPriorityAsc();
}
