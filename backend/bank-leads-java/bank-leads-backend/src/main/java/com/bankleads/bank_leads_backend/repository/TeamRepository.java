package com.bankleads.bank_leads_backend.repository;

import com.bankleads.bank_leads_backend.model.Team;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TeamRepository extends MongoRepository<Team, String> {
}
