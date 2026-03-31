package com.bankleads.bank_leads_backend.repository;

import com.bankleads.bank_leads_backend.model.UserInvitation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface UserInvitationRepository extends MongoRepository<UserInvitation, String> {

    @Query("{ 'tokenHash': ?0 }")
    Optional<UserInvitation> findByTokenHash(String tokenHash);

    @Query("{ 'email': { $regex: '^?0$', $options: 'i' } }")
    Optional<UserInvitation> findActiveByEmail(String email);

    @Query("{ 'email': { $regex: '^?0$', $options: 'i' } , 'consumedAt': null }")
    Optional<UserInvitation> findUnconsumedByEmail(String email);
}

