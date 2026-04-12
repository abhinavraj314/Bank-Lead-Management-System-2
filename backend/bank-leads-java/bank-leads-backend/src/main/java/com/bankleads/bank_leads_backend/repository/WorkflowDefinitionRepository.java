package com.bankleads.bank_leads_backend.repository;

import com.bankleads.bank_leads_backend.model.WorkflowDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface WorkflowDefinitionRepository extends MongoRepository<WorkflowDefinition, String> {
    Optional<WorkflowDefinition> findByKey(String key);
}
