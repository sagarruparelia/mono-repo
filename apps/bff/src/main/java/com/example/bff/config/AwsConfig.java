package com.example.bff.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

// DefaultCredentialsProvider: env vars, system props, ~/.aws/credentials, or IAM role
@Configuration
public class AwsConfig {

    private final BffProperties properties;

    public AwsConfig(BffProperties properties) {
        this.properties = properties;
    }

    @Bean
    public S3AsyncClient s3AsyncClient() {
        return S3AsyncClient.builder()
                .region(Region.of(properties.getDocument().getS3Region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(properties.getDocument().getS3Region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
