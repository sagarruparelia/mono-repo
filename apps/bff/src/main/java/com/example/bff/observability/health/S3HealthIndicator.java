package com.example.bff.observability.health;

import com.example.bff.config.properties.DocumentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.time.Duration;

/**
 * Health indicator for S3 connectivity.
 * Checks if the configured S3 bucket is accessible.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3HealthIndicator implements ReactiveHealthIndicator {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final S3AsyncClient s3Client;
    private final DocumentProperties documentProperties;

    @Override
    public Mono<Health> health() {
        String bucketName = documentProperties.s3().bucketName();

        return Mono.fromFuture(() -> s3Client.headBucket(
                        HeadBucketRequest.builder()
                                .bucket(bucketName)
                                .build()))
                .timeout(TIMEOUT)
                .map(response -> Health.up()
                        .withDetail("bucket", bucketName)
                        .withDetail("region", documentProperties.s3().region())
                        .build())
                .onErrorResume(error -> {
                    log.warn("S3 health check failed for bucket {}: {}", bucketName, error.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("bucket", bucketName)
                            .withDetail("error", error.getMessage())
                            .build());
                });
    }
}
