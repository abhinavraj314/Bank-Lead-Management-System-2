package com.bankleads.bank_leads_backend.repository;

import com.bankleads.bank_leads_backend.model.CanonicalField;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface CanonicalFieldRepository extends MongoRepository<CanonicalField, String> {
    Page<CanonicalField> findByIsActive(Boolean isActive, Pageable pageable);
    Page<CanonicalField> findAll(Pageable pageable);
    Optional<CanonicalField> findByFieldName(String fieldName);
    boolean existsByFieldName(String fieldName);
}
