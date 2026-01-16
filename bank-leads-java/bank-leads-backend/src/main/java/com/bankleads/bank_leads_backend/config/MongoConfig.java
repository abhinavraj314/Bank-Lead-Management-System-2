package com.bankleads.bank_leads_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing
public class MongoConfig {
    // MongoDB auditing enabled for @CreatedDate and @LastModifiedDate
}
