package com.bankleads.bank_leads_backend.repository;

import com.bankleads.bank_leads_backend.model.Source;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SourceRepository extends MongoRepository<Source, String> {
    
    // Find by sId (source ID field)
    @Query("{'sId': ?0}")
    Optional<Source> findBySId(String sId);
    
    @Query(value = "{'sId': ?0}", exists = true)
    boolean existsBySId(String sId);
    
    // Alias methods for backward compatibility
    @Query("{'sId': ?0}")
    Optional<Source> findBySourceId(String sourceId);
    
    @Query(value = "{'sId': ?0}", exists = true)
    boolean existsBySourceId(String sourceId);
    
    // Find by product ID
    @Query(value = "{'pId': ?0}", count = true)
    long countByPId(String pId);
    
    @Query("{'pId': ?0}")
    List<Source> findByPId(String pId);
}
