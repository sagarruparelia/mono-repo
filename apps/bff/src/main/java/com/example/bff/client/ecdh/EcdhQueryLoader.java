package com.example.bff.client.ecdh;

import com.example.bff.health.model.HealthResourceType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

// Queries stored in resources/graphql/ecdh/{resourceType}.graphql
@Slf4j
@Component
public class EcdhQueryLoader {

    private static final String QUERY_PATH_PREFIX = "graphql/ecdh/";
    private static final String QUERY_FILE_EXTENSION = ".graphql";

    private final Map<HealthResourceType, String> queries = new EnumMap<>(HealthResourceType.class);

    @PostConstruct
    void loadQueries() {
        for (HealthResourceType type : HealthResourceType.values()) {
            String path = QUERY_PATH_PREFIX + type.getQueryFileName() + QUERY_FILE_EXTENSION;
            try {
                String query = loadFromClasspath(path);
                queries.put(type, query);
                log.debug("Loaded GraphQL query for {} from {}", type, path);
            } catch (IOException e) {
                log.error("Failed to load GraphQL query for {} from {}", type, path, e);
                throw new IllegalStateException("Failed to load GraphQL query: " + path, e);
            }
        }
        log.info("Loaded {} ECDH GraphQL queries", queries.size());
    }

    public String getQuery(HealthResourceType resourceType) {
        String query = queries.get(resourceType);
        if (query == null) {
            throw new IllegalArgumentException("No query loaded for resource type: " + resourceType);
        }
        return query;
    }

    private String loadFromClasspath(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream is = resource.getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }
}
