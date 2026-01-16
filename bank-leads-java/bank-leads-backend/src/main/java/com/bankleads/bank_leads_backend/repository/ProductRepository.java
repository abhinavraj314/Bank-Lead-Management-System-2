package com.bankleads.bank_leads_backend.repository;

import com.bankleads.bank_leads_backend.model.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
    
    @Query(value = "{'pId': ?0}", exists = true)
    boolean existsByPId(String pId);
    
    @Query("{'pId': ?0}")
    Optional<Product> findByPId(String pId);
}
