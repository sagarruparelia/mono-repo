package com.example.bff.document.client;

import com.example.bff.config.BffProperties;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class S3StorageClient {

    private final S3AsyncClient s3AsyncClient;
    private final S3Presigner s3Presigner;
    private final BffProperties.Document documentConfig;

    public S3StorageClient(S3AsyncClient s3AsyncClient, S3Presigner s3Presigner, BffProperties properties) {
        this.s3AsyncClient = s3AsyncClient;
        this.s3Presigner = s3Presigner;
        this.documentConfig = properties.getDocument();
    }

    public String getBucket() {
        return documentConfig.getS3Bucket();
    }

    public Mono<PresignedUploadResult> generatePresignedUploadUrl(
            String key, String contentType, Long contentLength) {

        return Mono.fromCallable(() -> {
            Duration duration = Duration.ofMinutes(documentConfig.getPresignedUploadTtlMinutes());

            PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                    .bucket(documentConfig.getS3Bucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(contentLength);

            // Add KMS encryption if configured
            String kmsKeyId = documentConfig.getKmsKeyId();
            if (kmsKeyId != null && !kmsKeyId.isBlank()) {
                requestBuilder
                        .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                        .ssekmsKeyId(kmsKeyId);
                log.debug("Using SSE-KMS encryption with key: {}", kmsKeyId);
            }

            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(builder -> builder
                    .signatureDuration(duration)
                    .putObjectRequest(requestBuilder.build()));

            log.debug("Generated presigned upload URL for key: {}, expires in {} minutes", key, duration.toMinutes());

            return PresignedUploadResult.builder()
                    .url(presigned.url().toString())
                    .expiresAt(Instant.now().plus(duration))
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<PresignedDownloadResult> generatePresignedDownloadUrl(String key, String filename) {
        return Mono.fromCallable(() -> {
            Duration duration = Duration.ofMinutes(documentConfig.getPresignedDownloadTtlMinutes());

            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(documentConfig.getS3Bucket())
                    .key(key)
                    .responseContentDisposition("attachment; filename=\"" + filename + "\"")
                    .build();

            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(builder -> builder
                    .signatureDuration(duration)
                    .getObjectRequest(getRequest));

            log.debug("Generated presigned download URL for key: {}, expires in {} minutes", key, duration.toMinutes());

            return PresignedDownloadResult.builder()
                    .url(presigned.url().toString())
                    .expiresAt(Instant.now().plus(duration))
                    .build();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Boolean> checkObjectExists(String key) {
        return Mono.fromFuture(() ->
                        s3AsyncClient.headObject(HeadObjectRequest.builder()
                                .bucket(documentConfig.getS3Bucket())
                                .key(key)
                                .build())
                )
                .map(response -> true)
                .onErrorResume(NoSuchKeyException.class, e -> {
                    log.debug("Object not found: {}", key);
                    return Mono.just(false);
                })
                .onErrorResume(e -> {
                    log.error("Error checking object existence: {}", key, e);
                    return Mono.just(false);
                });
    }

    public Mono<HeadObjectResponse> getObjectMetadata(String key) {
        return Mono.fromFuture(() ->
                s3AsyncClient.headObject(HeadObjectRequest.builder()
                        .bucket(documentConfig.getS3Bucket())
                        .key(key)
                        .build())
        );
    }

    public Mono<Void> moveObject(String sourceKey, String targetKey) {
        String bucket = documentConfig.getS3Bucket();

        CopyObjectRequest.Builder copyBuilder = CopyObjectRequest.builder()
                .sourceBucket(bucket)
                .sourceKey(sourceKey)
                .destinationBucket(bucket)
                .destinationKey(targetKey);

        // Add KMS encryption if configured
        String kmsKeyId = documentConfig.getKmsKeyId();
        if (kmsKeyId != null && !kmsKeyId.isBlank()) {
            copyBuilder
                    .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                    .ssekmsKeyId(kmsKeyId);
        }

        return Mono.fromFuture(() -> s3AsyncClient.copyObject(copyBuilder.build()))
                .flatMap(copyResponse -> Mono.fromFuture(() ->
                        s3AsyncClient.deleteObject(DeleteObjectRequest.builder()
                                .bucket(bucket)
                                .key(sourceKey)
                                .build())
                ))
                .then()
                .doOnSuccess(v -> log.debug("Moved S3 object from {} to {}", sourceKey, targetKey))
                .doOnError(e -> log.error("Failed to move S3 object from {} to {}", sourceKey, targetKey, e));
    }

    public Mono<Void> deleteObject(String key) {
        return Mono.fromFuture(() ->
                        s3AsyncClient.deleteObject(DeleteObjectRequest.builder()
                                .bucket(documentConfig.getS3Bucket())
                                .key(key)
                                .build())
                )
                .then()
                .doOnSuccess(v -> log.debug("Deleted S3 object: {}", key))
                .doOnError(e -> log.error("Failed to delete S3 object: {}", key, e));
    }

    @Data
    @Builder
    public static class PresignedUploadResult {
        private String url;
        private Instant expiresAt;
    }

    @Data
    @Builder
    public static class PresignedDownloadResult {
        private String url;
        private Instant expiresAt;
    }
}
