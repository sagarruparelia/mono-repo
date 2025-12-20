package com.example.bff.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class SessionConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
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
