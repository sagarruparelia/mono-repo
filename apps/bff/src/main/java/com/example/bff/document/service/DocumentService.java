package com.example.bff.document.service;

import com.example.bff.config.properties.DocumentProperties;
import com.example.bff.document.dto.DocumentDto;
import com.example.bff.document.dto.DocumentUploadRequest;
import com.example.bff.document.model.DocumentEntity;
import com.example.bff.document.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.regex.Pattern;

/**
 * Service for managing documents (upload, download, delete).
 * Documents always belong to youth - others act as delegates.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("[^a-zA-Z0-9._-]");

    private final DocumentRepository repository;
    private final S3StorageService storageService;
    private final DocumentProperties properties;

    public DocumentService(
            DocumentRepository repository,
            S3StorageService storageService,
            DocumentProperties properties) {
        this.repository = repository;
        this.storageService = storageService;
        this.properties = properties;
    }

    /**
     * List all documents for a member (youth).
     *
     * @param memberId the youth's ID
     * @return stream of document DTOs
     */
    public Flux<DocumentDto> listDocuments(String memberId) {
        log.debug("Listing documents for member: {}", sanitizeForLog(memberId));
        return repository.findByMemberIdOrderByCreatedAtDesc(memberId)
                .map(DocumentDto::fromEntity);
    }

    /**
     * Get a specific document by ID.
     *
     * @param memberId   the youth's ID
     * @param documentId the document ID
     * @return document DTO or empty if not found
     */
    public Mono<DocumentDto> getDocument(String memberId, String documentId) {
        log.debug("Getting document {} for member {}", sanitizeForLog(documentId), sanitizeForLog(memberId));
        return repository.findByIdAndMemberId(documentId, memberId)
                .map(DocumentDto::fromEntity);
    }

    /**
     * Upload a new document for a youth.
     *
     * @param memberId          the youth's ID (document owner)
     * @param file              the uploaded file
     * @param request           upload metadata
     * @param uploadedBy        user ID who uploaded
     * @param uploadedByPersona persona of uploader
     * @return the created document DTO
     */
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
                sanitizeForLog(memberId),
                sanitizeForLog(originalFileName),
                sanitizeForLog(contentType),
                sanitizeForLog(uploadedBy));

        // Validate content type
        if (!properties.limits().allowedContentTypes().contains(contentType)) {
            return Mono.error(new IllegalArgumentException(
                    String.format("Content type '%s' is not allowed. Allowed types: %s",
                            contentType, properties.limits().allowedContentTypes())));
        }

        // Check file count limit
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

        // Collect file content
        return file.content()
                .reduce(ByteBuffer.allocate(0), (acc, buffer) -> {
                    ByteBuffer combined = ByteBuffer.allocate(
                            acc.remaining() + buffer.readableByteCount());
                    combined.put(acc);
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    combined.put(bytes);
                    combined.flip();
                    return combined;
                })
                .flatMap(content -> {
                    long fileSize = content.remaining();

                    // Validate file size
                    if (fileSize > properties.limits().maxFileSize()) {
                        return Mono.error(new IllegalArgumentException(
                                String.format("File size (%d bytes) exceeds maximum allowed (%d bytes)",
                                        fileSize, properties.limits().maxFileSize())));
                    }

                    if (fileSize == 0) {
                        return Mono.error(new IllegalArgumentException("File is empty"));
                    }

                    // Upload to S3 and save metadata
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

    /**
     * Download a document's content.
     *
     * @param memberId   the youth's ID
     * @param documentId the document ID
     * @return the file content as byte array
     */
    public Mono<byte[]> downloadDocument(String memberId, String documentId) {
        log.debug("Downloading document {} for member {}", sanitizeForLog(documentId), sanitizeForLog(memberId));
        return repository.findByIdAndMemberId(documentId, memberId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format("Document %s not found for member %s", documentId, memberId))))
                .flatMap(doc -> storageService.downloadFile(doc.s3Key()));
    }

    /**
     * Delete a document.
     *
     * @param memberId   the youth's ID
     * @param documentId the document ID
     */
    public Mono<Void> deleteDocument(String memberId, String documentId) {
        log.info("Deleting document {} for member {}", sanitizeForLog(documentId), sanitizeForLog(memberId));
        return repository.findByIdAndMemberId(documentId, memberId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        String.format("Document %s not found for member %s", documentId, memberId))))
                .flatMap(doc ->
                        storageService.deleteFile(doc.s3Key())
                                .then(repository.delete(doc))
                );
    }

    /**
     * Sanitize filename for S3 storage.
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unnamed";
        }
        return SAFE_FILENAME_PATTERN.matcher(fileName).replaceAll("_");
    }

    /**
     * Sanitize value for logging to prevent log injection.
     */
    private String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace("\n", "").replace("\r", "").replace("\t", "")
                .substring(0, Math.min(value.length(), 100));
    }
}
