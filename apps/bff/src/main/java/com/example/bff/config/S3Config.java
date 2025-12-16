package com.example.bff.config;

import com.example.bff.config.properties.DocumentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3AsyncClientBuilder;

import java.net.URI;
import java.time.Duration;

/**
 * Configuration for AWS S3 async client.
 * Supports both AWS credentials and custom endpoint (for LocalStack/MinIO).
 */
@Configuration
@ConditionalOnProperty(name = "app.documents.s3.bucket-name")
public class S3Config {

    @Bean
    public S3AsyncClient s3AsyncClient(DocumentProperties properties) {
        var s3Props = properties.s3();

        S3AsyncClientBuilder builder = S3AsyncClient.builder()
                .region(Region.of(s3Props.region()))
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(10))
                        .readTimeout(Duration.ofSeconds(30))
                        .writeTimeout(Duration.ofSeconds(30)));

        // Use explicit credentials if provided, otherwise use default provider chain
        if (s3Props.accessKeyId() != null && !s3Props.accessKeyId().isBlank()
                && s3Props.secretAccessKey() != null && !s3Props.secretAccessKey().isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(s3Props.accessKeyId(), s3Props.secretAccessKey())
            ));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        // Custom endpoint for LocalStack/MinIO (local development)
        if (s3Props.endpoint() != null && !s3Props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(s3Props.endpoint()))
                    .forcePathStyle(true);  // Required for LocalStack/MinIO
        }

        return builder.build();
    }
}
