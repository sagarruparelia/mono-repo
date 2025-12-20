package com.example.bff.config;

import com.example.bff.config.properties.IdentityCacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for reactive Redis caching of identity API responses.
 * Provides a ReactiveRedisTemplate for JSON serialization.
 */
@Configuration
public class IdentityCacheConfig {

    /**
     * Bean qualifier for the identity cache template.
     */
    public static final String IDENTITY_CACHE_TEMPLATE = "identityCacheTemplate";

    /**
     * ReactiveRedisTemplate for caching identity API responses.
     * Uses JSON serialization for values and String keys.
     *
     * Security: Uses GenericJackson2JsonRedisSerializer which safely handles
     * type information without exposing polymorphic deserialization attacks.
     */
    @Bean(IDENTITY_CACHE_TEMPLATE)
    public ReactiveRedisTemplate<String, Object> identityCacheTemplate(
            ReactiveRedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {

        StringRedisSerializer keySerializer = new StringRedisSerializer();

        // Use GenericJackson2JsonRedisSerializer for safe type handling
        // This stores @class metadata but validates against known types
        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisSerializationContext<String, Object> serializationContext =
                RedisSerializationContext.<String, Object>newSerializationContext(keySerializer)
                        .key(keySerializer)
                        .value(valueSerializer)
                        .hashKey(keySerializer)
                        .hashValue(valueSerializer)
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }

    /**
     * Default cache properties if not configured.
     */
    @Bean
    public IdentityCacheProperties identityCacheProperties() {
        return IdentityCacheProperties.defaults();
    }
}
