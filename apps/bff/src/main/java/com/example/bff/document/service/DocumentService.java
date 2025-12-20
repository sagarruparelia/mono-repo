package com.example.bff.document.service;

import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.DocumentProperties;
import com.example.bff.document.dto.DocumentDto;
import com.example.bff.document.dto.DocumentUploadRequest;
import com.example.bff.document.model.DocumentEntity;
import com.example.bff.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.core.io.buffer.DataBufferUtils;

import java.util.regex.Pattern;

/**
 * Manages document upload, download, and deletion for youth members.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("[^a-zA-Z0-9._-]");

    private final DocumentRepository repository;
    private final S3StorageService storageService;
    private final DocumentProperties properties;

    public Flux<DocumentDto> listDocuments(String memberId) {
        log.debug("Listing documents for member: {}", StringSanitizer.forLog(memberId));
        return repository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .map(DocumentDto::fromEntity);
    }

    public Mono<DocumentDto> getDocument(String memberId, String documentId) {
        log.debug("Getting document {} for member {}", StringSanitizer.forLog(documentId), StringSanitizer.forLog(memberId));
        return repository.findByIdAndMemberId(documentId, memberId)
                .map(DocumentDto::fromEntity);
    }

    public Mono<DocumentDto> uploadDocument(
            String memberId,
            FilePart file,
            DocumentUploadRequest request,
            String uploadedBy,
            String uploadedByPersona) {

        String originalFileName = file.filename();
        String contentType = file.headers().getContentType() != null
                ? file.headers().getContentType().toString()
                : "application/octet-stream";

        log.info("Uploading document for member {}: filename={}, contentType={}, uploadedBy={}",
                StringSanitizer.forLog(memberId),
                StringSanitizer.forLog(originalFileName),
                StringSanitizer.forLog(contentType),
                StringSanitizer.forLog(uploadedBy));

        if (!properties.limits().allowedContentTypes().contains(contentType)) {
            return Mono.error(new IllegalArgumentException(
                    String.format("Content type '%s' is not allowed. Allowed types: %s",
                            contentType, properties.limits().allowedContentTypes())));
        }

        return repository.countByMemberId(memberId)
                .flatMap(count -> {
                    if (count >= properties.limits().maxFilesPerMember()) {
                        return Mono.error(new IllegalArgumentException(
                                String.format("Maximum file limit (%d) reached for member",
                                        properties.limits().maxFilesPerMember())));
                    }
                    return processUpload(memberId, file, originalFileName, contentType,
                            request, uploadedBy, uploadedByPersona);
                });
    }

    private Mono<DocumentDto> processUpload(
            String memberId,
            FilePart file,
            String originalFileName,
            String contentType,
            DocumentUploadRequest request,
            String uploadedBy,
            String uploadedByPersona) {

        String sanitizedFileName = sanitizeFileName(originalFileName);

        // Use DataBufferUtils.join for efficient buffer aggregation (avoids O(n²) allocations)
        return DataBufferUtils.join(file.content())
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);  // Release pooled buffer
                    return bytes;
                })
                .flatMap(content -> {
                    long fileSize = content.length;

                    if (fileSize > properties.limits().maxFileSize()) {
                        return Mono.error(new IllegalArgumentException(
                                String.format("File size (%d bytes) exceeds maximum allowed (%d bytes)",
                                        fileSize, properties.limits().maxFileSize())));
                    }

                    if (fileSize == 0) {
                        return Mono.error(new IllegalArgumentException("File is empty"));
                    }

                    return storageService.uploadFile(memberId, sanitizedFileName, contentType, content)
                            .flatMap(s3Key -> {
                                DocumentEntity entity = DocumentEntity.create(
                                        memberId,
                                        sanitizedFileName,
                                        originalFileName,
                                        contentType,
                                        fileSize,
                                        s3Key,
                                        request.description(),
                                        request.documentType(),
                                        uploadedBy,
                                        uploadedByPersona
                                );
                                return repository.save(entity);
                            })
                            .map(DocumentDto::fromEntity);
                });
    }

    public Mono<byte[]> downloadDocument(String memberId, String documentId) {
        log.debug("Downloading document {} for member {}", StringSanitizer.forLog(documentId), StringSanitizer.forLog(memberId));
        return repository.findByIdAndMemberId(documentId, memberId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format("Document %s not found for member %s", documentId, memberId))))
                .flatMap(doc -> storageService.downloadFile(doc.s3Key()));
    }

    public Mono<Void> deleteDocument(String memberId, String documentId) {
        log.info("Deleting document {} for member {}", StringSanitizer.forLog(documentId), StringSanitizer.forLog(memberId));
        return repository.findByIdAndMemberId(documentId, memberId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format("Document %s not found for member %s", documentId, memberId))))
                .flatMap(doc ->
                        storageService.deleteFile(doc.s3Key())
                                .then(repository.delete(doc))
                );
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unnamed";
        }
        return SAFE_FILENAME_PATTERN.matcher(fileName).replaceAll("_");
    }
}
