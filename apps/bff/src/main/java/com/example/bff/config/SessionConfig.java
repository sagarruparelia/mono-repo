package com.example.bff.config;

import com.example.bff.config.properties.SessionProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
@Configuration
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
public class SessionConfig {

    private final SessionProperties sessionProperties;

    public SessionConfig(SessionProperties sessionProperties) {
        this.sessionProperties = sessionProperties;
    }

    @Bean
    public ReactiveRedisOperations<String, String> reactiveRedisOperations(
            ReactiveRedisConnectionFactory connectionFactory) {
        RedisSerializationContext<String, String> serializationContext =
                RedisSerializationContext.<String, String>newSerializationContext(new StringRedisSerializer())
                        .key(new StringRedisSerializer())
                        .value(new StringRedisSerializer())
                        .hashKey(new StringRedisSerializer())
                        .hashValue(new StringRedisSerializer())
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}
