package com.example.bff.document.service;

import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.DocumentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

/**
 * Handles document storage and retrieval from S3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private static final String KEY_PREFIX = "documents";

    private final S3AsyncClient s3Client;
    private final DocumentProperties properties;

    public Mono<String> uploadFile(String memberId, String fileName, String contentType, byte[] content) {
        String s3Key = generateS3Key(memberId, fileName);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.s3().bucketName())
                .key(s3Key)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();

        return Mono.fromFuture(() ->
                        s3Client.putObject(request, AsyncRequestBody.fromBytes(content))
                )
                .map(response -> s3Key)
                .doOnSuccess(key -> log.info("Uploaded document to S3: bucket={}, key={}",
                        StringSanitizer.forLog(properties.s3().bucketName()), StringSanitizer.forLog(key)))
                .doOnError(e -> log.error("Failed to upload document to S3: {}",
                        StringSanitizer.forLog(e.getMessage())));
    }

    public Mono<byte[]> downloadFile(String s3Key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.s3().bucketName())
                .key(s3Key)
                .build();

        return Mono.fromFuture(() ->
                        s3Client.getObject(request, AsyncResponseTransformer.toBytes())
                )
                .map(response -> response.asByteArray())
                .doOnSuccess(bytes -> log.debug("Downloaded document from S3: key={}, size={}",
                        StringSanitizer.forLog(s3Key), bytes.length))
                .doOnError(e -> log.error("Failed to download document from S3: key={}, error={}",
                        StringSanitizer.forLog(s3Key), StringSanitizer.forLog(e.getMessage())));
    }

    public Mono<Void> deleteFile(String s3Key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.s3().bucketName())
                .key(s3Key)
                .build();

        return Mono.fromFuture(() -> s3Client.deleteObject(request))
                .then()
                .doOnSuccess(v -> log.info("Deleted document from S3: key={}", StringSanitizer.forLog(s3Key)))
                .doOnError(e -> log.error("Failed to delete document from S3: key={}, error={}",
                        StringSanitizer.forLog(s3Key), StringSanitizer.forLog(e.getMessage())));
    }

    private String generateS3Key(String memberId, String fileName) {
        String uuid = UUID.randomUUID().toString();
        return String.format("%s/%s/%s/%s", KEY_PREFIX, memberId, uuid, fileName);
    }
}
