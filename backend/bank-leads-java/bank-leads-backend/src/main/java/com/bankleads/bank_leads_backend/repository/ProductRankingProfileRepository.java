package com.bankleads.bank_leads_backend.repository;

import com.bankleads.bank_leads_backend.model.ProductRankingProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface ProductRankingProfileRepository extends MongoRepository<ProductRankingProfile, String> {
    /** Explicit query: derived {@code findByPId} is parsed as property {@code PId}, which does not exist. */
    @Query("{ 'pId': ?0 }")
    Optional<ProductRankingProfile> findByPId(String pId);
}
