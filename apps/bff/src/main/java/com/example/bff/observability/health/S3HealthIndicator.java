package com.example.bff.observability.health;

import com.example.bff.config.properties.DocumentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Component
public class S3HealthIndicator implements ReactiveHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(S3HealthIndicator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final S3AsyncClient s3Client;
    private final DocumentProperties documentProperties;

    public S3HealthIndicator(S3AsyncClient s3Client, DocumentProperties documentProperties) {
        this.s3Client = s3Client;
        this.documentProperties = documentProperties;
    }

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
