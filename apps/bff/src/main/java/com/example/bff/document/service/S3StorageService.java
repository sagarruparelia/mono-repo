package com.example.bff.document.service;

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

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Service for storing and retrieving documents from S3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class S3StorageService {

    private static final String KEY_PREFIX = "documents";

    private final S3AsyncClient s3Client;
    private final DocumentProperties properties;

    /**
     * Upload a file to S3.
     *
     * @param memberId    the youth/member ID (document owner)
     * @param fileName    sanitized filename
     * @param contentType MIME type
     * @param content     file content as ByteBuffer
     * @return the S3 key where the file was stored
     */
    public Mono<String> uploadFile(String memberId, String fileName, String contentType, ByteBuffer content) {
        String s3Key = generateS3Key(memberId, fileName);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.s3().bucketName())
                .key(s3Key)
                .contentType(contentType)
                .contentLength((long) content.remaining())
                .build();

        return Mono.fromFuture(() ->
                        s3Client.putObject(request, AsyncRequestBody.fromByteBuffer(content.duplicate()))
                )
                .map(response -> s3Key)
                .doOnSuccess(key -> log.info("Uploaded document to S3: bucket={}, key={}",
                        sanitizeForLog(properties.s3().bucketName()), sanitizeForLog(key)))
                .doOnError(e -> log.error("Failed to upload document to S3: {}",
                        sanitizeForLog(e.getMessage())));
    }

    /**
     * Download a file from S3.
     *
     * @param s3Key the S3 object key
     * @return the file content as byte array
     */
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
                        sanitizeForLog(s3Key), bytes.length))
                .doOnError(e -> log.error("Failed to download document from S3: key={}, error={}",
                        sanitizeForLog(s3Key), sanitizeForLog(e.getMessage())));
    }

    /**
     * Delete a file from S3.
     *
     * @param s3Key the S3 object key
     */
    public Mono<Void> deleteFile(String s3Key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(properties.s3().bucketName())
                .key(s3Key)
                .build();

        return Mono.fromFuture(() -> s3Client.deleteObject(request))
                .then()
                .doOnSuccess(v -> log.info("Deleted document from S3: key={}", sanitizeForLog(s3Key)))
                .doOnError(e -> log.error("Failed to delete document from S3: key={}, error={}",
                        sanitizeForLog(s3Key), sanitizeForLog(e.getMessage())));
    }

    /**
     * Generate a unique S3 key for a document.
     * Format: documents/{memberId}/{uuid}/{fileName}
     */
    private String generateS3Key(String memberId, String fileName) {
        String uuid = UUID.randomUUID().toString();
        return String.format("%s/%s/%s/%s", KEY_PREFIX, memberId, uuid, fileName);
    }

    /**
     * Sanitize a value for logging to prevent log injection.
     */
    private String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace("\n", "").replace("\r", "").replace("\t", "")
                .substring(0, Math.min(value.length(), 200));
    }
}
