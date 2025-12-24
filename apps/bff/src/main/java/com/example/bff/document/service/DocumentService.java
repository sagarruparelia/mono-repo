package com.example.bff.document.service;

import com.example.bff.config.BffProperties;
import com.example.bff.document.client.S3StorageClient;
import com.example.bff.document.document.DocumentMetadataDoc;
import com.example.bff.document.document.TempUploadDoc;
import com.example.bff.document.model.DocumentStatus;
import com.example.bff.document.model.ScanStatus;
import com.example.bff.document.model.UploadStatus;
import com.example.bff.document.model.request.*;
import com.example.bff.document.model.response.*;
import com.example.bff.document.repository.DocumentMetadataRepository;
import com.example.bff.document.repository.TempUploadRepository;
import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.Persona;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class DocumentService {

    private final S3StorageClient s3Client;
    private final DocumentMetadataRepository documentRepository;
    private final TempUploadRepository tempUploadRepository;
    private final DocumentCategoryService categoryService;
    private final DocumentAccessValidator accessValidator;
    private final BffProperties.Document documentConfig;

    public DocumentService(S3StorageClient s3Client,
                           DocumentMetadataRepository documentRepository,
                           TempUploadRepository tempUploadRepository,
                           DocumentCategoryService categoryService,
                           DocumentAccessValidator accessValidator,
                           BffProperties properties) {
        this.s3Client = s3Client;
        this.documentRepository = documentRepository;
        this.tempUploadRepository = tempUploadRepository;
        this.categoryService = categoryService;
        this.accessValidator = accessValidator;
        this.documentConfig = properties.getDocument();
    }

    public Mono<InitiateUploadResponse> initiateUpload(AuthContext auth, InitiateUploadRequest request) {
        String targetOwnerEid = resolveEnterpriseId(auth, request.enterpriseId());

        return accessValidator.validateUpload(auth, targetOwnerEid)
                .then(validateContentType(request.contentType()))
                .then(validateFileSize(request.fileSizeBytes()))
                .then(checkConcurrentUploadLimit(auth.loggedInMemberIdValue()))
                .then(Mono.defer(() -> {
                    String uploadId = UUID.randomUUID().toString();
                    String s3Key = buildTempS3Key(auth.loggedInMemberIdValue(), uploadId, request.filename());
                    Instant now = Instant.now();
                    Instant presignedExpiry = now.plusSeconds(documentConfig.getPresignedUploadTtlMinutes() * 60L);
                    Instant uploadExpiry = now.plus(documentConfig.getTempExpiryHours(), ChronoUnit.HOURS);

                    return s3Client.generatePresignedUploadUrl(s3Key, request.contentType(), request.fileSizeBytes())
                            .flatMap(presignedResult -> {
                                TempUploadDoc tempUpload = TempUploadDoc.builder()
                                        .id(uploadId)
                                        .s3Key(s3Key)
                                        .uploaderId(auth.loggedInMemberIdValue())
                                        .uploaderIdType(auth.loggedInMemberIdType())
                                        .uploaderPersona(auth.persona())
                                        .targetOwnerEid(targetOwnerEid)
                                        .originalFilename(request.filename())
                                        .contentType(request.contentType())
                                        .expectedFileSizeBytes(request.fileSizeBytes())
                                        .uploadStatus(UploadStatus.PENDING)
                                        .presignedUrlExpiresAt(presignedExpiry)
                                        .expiresAt(uploadExpiry)
                                        .build();

                                return tempUploadRepository.save(tempUpload)
                                        .map(saved -> new InitiateUploadResponse(
                                                saved.getId(),
                                                presignedResult.getUrl(),
                                                presignedResult.getExpiresAt(),
                                                uploadExpiry,
                                                (long) documentConfig.getMaxFileSizeMb() * 1024 * 1024,
                                                request.contentType()));
                            });
                }))
                .doOnSuccess(resp -> log.info("Initiated upload: uploadId={}, user={}, targetOwner={}",
                        resp.uploadId(), auth.loggedInMemberIdValue(), targetOwnerEid))
                .doOnError(e -> log.error("Failed to initiate upload for user={}: {}",
                        auth.loggedInMemberIdValue(), e.getMessage()));
    }

    public Mono<FinalizeUploadResponse> finalizeUpload(AuthContext auth, FinalizeUploadRequest request) {
        String targetOwnerEid = resolveEnterpriseId(auth, request.enterpriseId());

        return accessValidator.validateUpload(auth, targetOwnerEid)
                .then(validateCategory(request.category()))
                .then(tempUploadRepository.findById(request.uploadId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Upload not found or expired")))
                .flatMap(tempUpload -> {
                    // Verify the temp upload belongs to this user and target owner
                    if (!tempUpload.getUploaderId().equals(auth.loggedInMemberIdValue())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Upload does not belong to current user"));
                    }
                    if (!tempUpload.getTargetOwnerEid().equals(targetOwnerEid)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Enterprise ID mismatch"));
                    }
                    if (tempUpload.getUploadStatus() == UploadStatus.FINALIZED) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Upload already finalized"));
                    }

                    // Check if temp upload has expired
                    if (Instant.now().isAfter(tempUpload.getExpiresAt())) {
                        return Mono.error(new ResponseStatusException(HttpStatus.GONE,
                                "Upload has expired"));
                    }

                    // Verify the file exists in S3
                    return s3Client.checkObjectExists(tempUpload.getS3Key())
                            .flatMap(exists -> {
                                if (!exists) {
                                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                            "File not uploaded to S3"));
                                }

                                String documentId = UUID.randomUUID().toString();
                                String permanentKey = buildPermanentS3Key(targetOwnerEid, documentId,
                                        tempUpload.getOriginalFilename());
                                Instant now = Instant.now();

                                // Move the file from temp to permanent location
                                return s3Client.moveObject(tempUpload.getS3Key(), permanentKey)
                                        .then(Mono.defer(() -> {
                                            // Create the permanent document record
                                            DocumentMetadataDoc doc = DocumentMetadataDoc.builder()
                                                    .id(documentId)
                                                    .ownerEnterpriseId(targetOwnerEid)
                                                    .uploaderId(auth.loggedInMemberIdValue())
                                                    .uploaderIdType(auth.loggedInMemberIdType())
                                                    .uploaderPersona(auth.persona())
                                                    .s3Bucket(s3Client.getBucket())
                                                    .s3Key(permanentKey)
                                                    .originalFilename(tempUpload.getOriginalFilename())
                                                    .contentType(tempUpload.getContentType())
                                                    .fileSizeBytes(tempUpload.getExpectedFileSizeBytes())
                                                    .category(request.category())
                                                    .title(request.title())
                                                    .description(request.description())
                                                    .status(DocumentStatus.PENDING_SCAN)
                                                    .scanStatus(ScanStatus.NOT_SCANNED)
                                                    .build();

                                            return documentRepository.save(doc);
                                        }))
                                        .flatMap(savedDoc -> {
                                            // Update temp upload status
                                            tempUpload.setUploadStatus(UploadStatus.FINALIZED);
                                            return tempUploadRepository.save(tempUpload)
                                                    .thenReturn(savedDoc);
                                        });
                            });
                })
                .map(doc -> new FinalizeUploadResponse(
                        doc.getId(),
                        doc.getOriginalFilename(),
                        doc.getCategory(),
                        doc.getStatus().name(),
                        doc.getCreatedAt()))
                .doOnSuccess(resp -> log.info("Finalized upload: documentId={}, user={}, owner={}",
                        resp.documentId(), auth.loggedInMemberIdValue(), targetOwnerEid))
                .doOnError(e -> log.error("Failed to finalize upload={} for user={}: {}",
                        request.uploadId(), auth.loggedInMemberIdValue(), e.getMessage()));
    }

    public Mono<DocumentListResponse> listDocuments(AuthContext auth, DocumentListRequest request) {
        String ownerEid = resolveEnterpriseId(auth, request.enterpriseId());

        return accessValidator.validateAccess(auth, ownerEid)
                .then(Mono.defer(() -> {
                    PageRequest pageable = PageRequest.of(
                            request.page(),
                            request.size(),
                            Sort.by(Sort.Direction.DESC, "createdAt"));

                    // Get categories for display name lookup
                    return categoryService.getActiveCategories()
                            .flatMap(categories -> {
                                // Build category lookup map
                                var categoryMap = categories.stream()
                                        .collect(java.util.stream.Collectors.toMap(
                                                c -> c.getId(),
                                                c -> c.getDisplayName(),
                                                (a, b) -> a));

                                // Query documents with or without category filter
                                Mono<List<DocumentMetadataDoc>> docsMono;
                                Mono<Long> countMono;

                                if (request.category() != null && !request.category().isBlank()) {
                                    docsMono = documentRepository.findByOwnerEnterpriseIdAndStatusAndCategory(
                                                    ownerEid, DocumentStatus.ACTIVE, request.category(), pageable)
                                            .collectList();
                                    countMono = documentRepository.countByOwnerEnterpriseIdAndStatusAndCategory(
                                            ownerEid, DocumentStatus.ACTIVE, request.category());
                                } else {
                                    docsMono = documentRepository.findByOwnerEnterpriseIdAndStatus(
                                                    ownerEid, DocumentStatus.ACTIVE, pageable)
                                            .collectList();
                                    countMono = documentRepository.countByOwnerEnterpriseIdAndStatus(
                                            ownerEid, DocumentStatus.ACTIVE);
                                }

                                return Mono.zip(docsMono, countMono)
                                        .map(tuple -> {
                                            List<DocumentMetadataDoc> docs = tuple.getT1();
                                            long totalRecords = tuple.getT2();
                                            int totalPages = (int) Math.ceil((double) totalRecords / request.size());

                                            List<DocumentListResponse.DocumentSummary> summaries = docs.stream()
                                                    .map(doc -> new DocumentListResponse.DocumentSummary(
                                                            doc.getId(),
                                                            doc.getOriginalFilename(),
                                                            doc.getCategory(),
                                                            categoryMap.getOrDefault(doc.getCategory(), doc.getCategory()),
                                                            doc.getTitle(),
                                                            doc.getFileSizeBytes(),
                                                            doc.getContentType(),
                                                            doc.getStatus().name(),
                                                            doc.getScanStatus().name(),
                                                            doc.getCreatedAt()))
                                                    .toList();

                                            return new DocumentListResponse(
                                                    summaries,
                                                    request.page(),
                                                    request.size(),
                                                    (int) totalRecords,
                                                    totalPages,
                                                    request.page() < totalPages - 1,
                                                    request.page() > 0);
                                        });
                            });
                }))
                .doOnSuccess(resp -> log.debug("Listed {} documents for owner={}",
                        resp.documents().size(), ownerEid));
    }

    public Mono<DocumentDownloadResponse> getDownloadUrl(AuthContext auth, DocumentDownloadRequest request) {
        String ownerEid = resolveEnterpriseId(auth, request.enterpriseId());

        return accessValidator.validateAccess(auth, ownerEid)
                .then(documentRepository.findById(request.documentId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found")))
                .flatMap(doc -> {
                    // Verify document belongs to the requested owner
                    if (!doc.getOwnerEnterpriseId().equals(ownerEid)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
                    }

                    // Check document status
                    if (doc.getStatus() == DocumentStatus.DELETED) {
                        return Mono.error(new ResponseStatusException(HttpStatus.GONE, "Document has been deleted"));
                    }
                    if (doc.getStatus() == DocumentStatus.QUARANTINED) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "Document is quarantined due to security concerns"));
                    }

                    // Block download until scan is CLEAN
                    if (doc.getScanStatus() != ScanStatus.CLEAN) {
                        if (doc.getScanStatus() == ScanStatus.INFECTED) {
                            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                    "Document failed security scan"));
                        }
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Document is being processed. Please try again later."));
                    }

                    return s3Client.generatePresignedDownloadUrl(doc.getS3Key(), doc.getOriginalFilename())
                            .map(presignedResult -> new DocumentDownloadResponse(
                                    presignedResult.getUrl(),
                                    presignedResult.getExpiresAt(),
                                    doc.getOriginalFilename(),
                                    doc.getContentType(),
                                    doc.getFileSizeBytes()));
                })
                .doOnSuccess(resp -> log.info("Generated download URL for document in owner={}", ownerEid))
                .doOnError(e -> log.error("Failed to generate download URL for documentId={}: {}",
                        request.documentId(), e.getMessage()));
    }

    public Mono<DocumentDeleteResponse> deleteDocument(AuthContext auth, DocumentDeleteRequest request) {
        String ownerEid = resolveEnterpriseId(auth, request.enterpriseId());

        return accessValidator.validateAccess(auth, ownerEid)
                .then(documentRepository.findById(request.documentId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found")))
                .flatMap(doc -> {
                    // Verify document belongs to the requested owner
                    if (!doc.getOwnerEnterpriseId().equals(ownerEid)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
                    }

                    // Check if already deleted
                    if (doc.getStatus() == DocumentStatus.DELETED) {
                        return Mono.error(new ResponseStatusException(HttpStatus.GONE,
                                "Document has already been deleted"));
                    }

                    // Soft delete by updating status
                    doc.setStatus(DocumentStatus.DELETED);
                    Instant now = Instant.now();

                    return documentRepository.save(doc)
                            .map(saved -> new DocumentDeleteResponse(
                                    true,
                                    saved.getId(),
                                    now));
                })
                .doOnSuccess(resp -> log.info("Deleted document: documentId={}, user={}, owner={}",
                        resp.documentId(), auth.loggedInMemberIdValue(), ownerEid))
                .doOnError(e -> log.error("Failed to delete documentId={} for user={}: {}",
                        request.documentId(), auth.loggedInMemberIdValue(), e.getMessage()));
    }

    public Mono<DocumentDetailResponse> getDocumentDetail(AuthContext auth, DocumentDetailRequest request) {
        String ownerEid = resolveEnterpriseId(auth, request.enterpriseId());

        return accessValidator.validateAccess(auth, ownerEid)
                .then(documentRepository.findById(request.documentId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found")))
                .flatMap(doc -> {
                    // Verify document belongs to the requested owner
                    if (!doc.getOwnerEnterpriseId().equals(ownerEid)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
                    }

                    // Get category display name
                    return categoryService.getCategoryById(doc.getCategory())
                            .map(category -> category.getDisplayName())
                            .defaultIfEmpty(doc.getCategory())
                            .map(categoryDisplayName -> new DocumentDetailResponse(
                                    doc.getId(),
                                    doc.getOwnerEnterpriseId(),
                                    doc.getOriginalFilename(),
                                    doc.getContentType(),
                                    doc.getFileSizeBytes(),
                                    doc.getCategory(),
                                    categoryDisplayName,
                                    doc.getTitle(),
                                    doc.getDescription(),
                                    doc.getTags(),
                                    doc.getStatus().name(),
                                    doc.getScanStatus().name(),
                                    new DocumentDetailResponse.UploaderInfo(
                                            doc.getUploaderId(),
                                            doc.getUploaderIdType() != null
                                                    ? doc.getUploaderIdType().name() : null,
                                            doc.getUploaderPersona() != null
                                                    ? doc.getUploaderPersona().name() : null),
                                    doc.getCreatedAt(),
                                    doc.getUpdatedAt()));
                })
                .doOnSuccess(resp -> log.debug("Retrieved document detail: documentId={}", resp.documentId()));
    }

    // SELF uses their own enterpriseId; others use the provided enterpriseId
    private String resolveEnterpriseId(AuthContext auth, String requestedEid) {
        if (auth.persona() == Persona.SELF) {
            return auth.enterpriseId();
        }
        if (requestedEid == null || requestedEid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Enterprise ID is required for " + auth.persona() + " persona");
        }
        return requestedEid;
    }

    private Mono<Void> validateContentType(String contentType) {
        if (!documentConfig.getAllowedContentTypes().contains(contentType)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Content type not allowed: " + contentType));
        }
        return Mono.empty();
    }

    private Mono<Void> validateFileSize(Long fileSizeBytes) {
        long maxBytes = (long) documentConfig.getMaxFileSizeMb() * 1024 * 1024;
        if (fileSizeBytes > maxBytes) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File size exceeds maximum of " + documentConfig.getMaxFileSizeMb() + "MB"));
        }
        return Mono.empty();
    }

    private Mono<Void> checkConcurrentUploadLimit(String uploaderId) {
        return tempUploadRepository.countByUploaderIdAndUploadStatus(uploaderId, UploadStatus.PENDING)
                .flatMap(count -> {
                    if (count >= documentConfig.getMaxConcurrentUploads()) {
                        return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                                "Maximum concurrent uploads reached. Please finalize or wait for pending uploads."));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> validateCategory(String categoryId) {
        return categoryService.isValidCategory(categoryId)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Invalid category: " + categoryId));
                    }
                    return Mono.empty();
                });
    }

    // Format: temp/{uploaderId}/{uploadId}/{filename}
    private String buildTempS3Key(String uploaderId, String uploadId, String filename) {
        return String.format("temp/%s/%s/%s", uploaderId, uploadId, sanitizeFilename(filename));
    }

    // Format: docs/{ownerEnterpriseId}/{documentId}/{filename}
    private String buildPermanentS3Key(String ownerEnterpriseId, String documentId, String filename) {
        return String.format("docs/%s/%s/%s", ownerEnterpriseId, documentId, sanitizeFilename(filename));
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unnamed";
        }
        // Replace problematic characters with underscores
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
