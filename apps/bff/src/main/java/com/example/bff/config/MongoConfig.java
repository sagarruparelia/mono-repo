package com.example.bff.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableReactiveMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories;

// Future: MongoDB vector embeddings for AI agent capabilities
@Configuration
@EnableReactiveMongoRepositories(basePackages = {
        "com.example.bff.health.repository",
        "com.example.bff.document.repository"
})
@EnableReactiveMongoAuditing
public class MongoConfig {
    // TTL indexes are auto-created by Spring Data MongoDB from @Indexed(expireAfter)
}
