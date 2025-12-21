package com.example.bff.health.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches GraphQL queries from classpath resources.
 * Queries are preloaded at startup for performance.
 */
@Slf4j
@Component
public class GraphqlQueryLoader {

    private static final String GRAPHQL_BASE_PATH = "graphql/health/";

    private static final String IMMUNIZATION_QUERY_FILE = "immunization-query.graphql";
    private static final String ALLERGY_QUERY_FILE = "allergy-query.graphql";
    private static final String CONDITION_QUERY_FILE = "condition-query.graphql";

    private final Map<String, String> queryCache = new ConcurrentHashMap<>();

    @PostConstruct
    void preloadQueries() {
        log.info("Preloading GraphQL queries for health data");
        loadQuery(IMMUNIZATION_QUERY_FILE);
        loadQuery(ALLERGY_QUERY_FILE);
        loadQuery(CONDITION_QUERY_FILE);
        log.info("Loaded {} GraphQL queries", queryCache.size());
    }

    /**
     * Get the immunization query.
     */
    public String getImmunizationQuery() {
        return getQuery(IMMUNIZATION_QUERY_FILE);
    }

    /**
     * Get the allergy query.
     */
    public String getAllergyQuery() {
        return getQuery(ALLERGY_QUERY_FILE);
    }

    /**
     * Get the condition query.
     */
    public String getConditionQuery() {
        return getQuery(CONDITION_QUERY_FILE);
    }

    /**
     * Get a query by filename, loading from cache.
     */
    private String getQuery(String fileName) {
        return queryCache.computeIfAbsent(fileName, this::loadQuery);
    }

    /**
     * Load a query from classpath.
     */
    private String loadQuery(String fileName) {
        try {
            ClassPathResource resource = new ClassPathResource(GRAPHQL_BASE_PATH + fileName);
            // Use try-with-resources to ensure InputStream is closed
            try (var inputStream = resource.getInputStream()) {
                String query = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
                log.debug("Loaded GraphQL query: {}", fileName);
                return query;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load GraphQL query: " + fileName, e);
        }
    }
}
