package com.example.bff.document.controller;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.abac.service.AbacAuthorizationService;
import com.example.bff.authz.model.AuthType;
import com.example.bff.document.dto.DocumentDto;
import com.example.bff.document.dto.DocumentUploadRequest;
import com.example.bff.document.model.DocumentEntity.DocumentType;
import com.example.bff.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * REST controller for document management.
 * Documents always belong to youth (member) - others act as delegates.
 */
@Slf4j
@RestController
@RequestMapping("/api/member/{memberId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private static final String SESSION_COOKIE = "BFF_SESSION";

    private final DocumentService documentService;
    private final AbacAuthorizationService authorizationService;

    /**
     * List all documents for a youth.
     * GET /api/member/{memberId}/documents
     */
    @GetMapping
    public Mono<ResponseEntity<List<DocumentDto>>> listDocuments(
            @PathVariable String memberId,
            ServerWebExchange exchange) {

        return authorizeAndExecute(exchange, memberId, Action.LIST, "list",
                () -> documentService.listDocuments(memberId)
                        .collectList()
                        .map(ResponseEntity::ok));
    }

    /**
     * Get a single document's metadata.
     * GET /api/member/{memberId}/documents/{documentId}
     */
    @GetMapping("/{documentId}")
    public Mono<ResponseEntity<DocumentDto>> getDocument(
            @PathVariable String memberId,
            @PathVariable String documentId,
            ServerWebExchange exchange) {

        return authorizeAndExecute(exchange, memberId, Action.VIEW, documentId,
                () -> documentService.getDocument(memberId, documentId)
                        .map(ResponseEntity::ok)
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
    }

    /**
     * Download a document's content.
     * GET /api/member/{memberId}/documents/{documentId}/download
     */
    @GetMapping("/{documentId}/download")
    public Mono<ResponseEntity<byte[]>> downloadDocument(
            @PathVariable String memberId,
            @PathVariable String documentId,
            ServerWebExchange exchange) {

        // Download requires VIEW_SENSITIVE since all documents are sensitive
        return authorizeAndExecute(exchange, memberId, Action.VIEW_SENSITIVE, documentId,
                () -> documentService.getDocument(memberId, documentId)
                        .flatMap(doc -> documentService.downloadDocument(memberId, documentId)
                                .map(content -> ResponseEntity.ok()
                                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"" + sanitizeFilename(doc.fileName()) + "\"")
                                        .contentType(MediaType.parseMediaType(doc.contentType()))
                                        .contentLength(content.length)
                                        .body(content)))
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
    }

    /**
     * Upload a new document for a youth.
     * POST /api/member/{memberId}/documents
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<DocumentDto>> uploadDocument(
            @PathVariable String memberId,
            @RequestPart("file") FilePart file,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "documentType", required = false) String documentType,
            ServerWebExchange exchange) {

        DocumentUploadRequest request = new DocumentUploadRequest(
                description,
                parseDocumentType(documentType)
        );

        return authorizeUpload(exchange, memberId, file, request);
    }

    /**
     * Delete a document.
     * DELETE /api/member/{memberId}/documents/{documentId}
     */
    @DeleteMapping("/{documentId}")
    public Mono<ResponseEntity<Void>> deleteDocument(
            @PathVariable String memberId,
            @PathVariable String documentId,
            ServerWebExchange exchange) {

        return authorizeAndExecute(exchange, memberId, Action.DELETE, documentId,
                () -> documentService.deleteDocument(memberId, documentId)
                        .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                        .onErrorResume(IllegalArgumentException.class,
                                e -> Mono.just(ResponseEntity.notFound().build())));
    }

    /**
     * Authorize and execute a document operation.
     */
    private <T> Mono<ResponseEntity<T>> authorizeAndExecute(
            ServerWebExchange exchange,
            String memberId,
            Action action,
            String documentId,
            java.util.function.Supplier<Mono<ResponseEntity<T>>> operation) {

        return buildSubjectFromExchange(exchange)
                .flatMap(subject -> {
                    ResourceAttributes resource = ResourceAttributes.document(documentId, memberId);
                    return authorizationService.authorize(subject, resource, action, exchange.getRequest())
                            .flatMap(decision -> {
                                if (decision.isAllowed()) {
                                    return operation.get();
                                }
                                log.warn("Authorization denied for action {} on document {} for member {}: {}",
                                        action, documentId, memberId, decision.reason());
                                return Mono.<ResponseEntity<T>>just(buildForbiddenResponse(decision));
                            });
                })
                .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<T>build())));
    }

    /**
     * Authorize upload operation with special handling for extracting uploader info.
     */
    private Mono<ResponseEntity<DocumentDto>> authorizeUpload(
            ServerWebExchange exchange,
            String memberId,
            FilePart file,
            DocumentUploadRequest request) {

        return buildSubjectFromExchange(exchange)
                .flatMap(subject -> {
                    ResourceAttributes resource = ResourceAttributes.document("new", memberId);
                    return authorizationService.authorize(subject, resource, Action.UPLOAD, exchange.getRequest())
                            .flatMap(decision -> {
                                if (decision.isAllowed()) {
                                    return documentService.uploadDocument(
                                                    memberId, file, request, subject.userId(), subject.persona())
                                            .map(doc -> ResponseEntity.status(HttpStatus.CREATED).body(doc))
                                            .onErrorResume(IllegalArgumentException.class,
                                                    e -> Mono.just(ResponseEntity.badRequest()
                                                            .<DocumentDto>body(null)));
                                }
                                log.warn("Authorization denied for upload to member {}: {}",
                                        memberId, decision.reason());
                                return Mono.<ResponseEntity<DocumentDto>>just(buildForbiddenResponse(decision));
                            });
                })
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<DocumentDto>build()));
    }

    /**
     * Build subject attributes from exchange (handles both HSID and Proxy).
     */
    private Mono<SubjectAttributes> buildSubjectFromExchange(ServerWebExchange exchange) {
        AuthType authType = authorizationService.determineAuthType(exchange.getRequest());

        if (authType == AuthType.PROXY) {
            return authorizationService.buildProxySubject(exchange.getRequest());
        }

        // HSID - get session from cookie
        var sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE);
        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            log.debug("No session cookie found");
            return Mono.empty();
        }

        return authorizationService.buildHsidSubject(sessionCookie.getValue());
    }

    /**
     * Build a forbidden response with policy decision details.
     */
    private <T> ResponseEntity<T> buildForbiddenResponse(PolicyDecision decision) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("X-Policy-Id", decision.policyId())
                .header("X-Policy-Reason", sanitizeHeader(decision.reason()))
                .build();
    }

    /**
     * Parse document type from string.
     */
    private DocumentType parseDocumentType(String documentType) {
        if (documentType == null || documentType.isBlank()) {
            return DocumentType.OTHER;
        }
        try {
            return DocumentType.valueOf(documentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DocumentType.OTHER;
        }
    }

    /**
     * Sanitize filename for Content-Disposition header.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "download";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Sanitize value for HTTP header.
     */
    private String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\n", " ").replace("\r", " ")
                .substring(0, Math.min(value.length(), 200));
    }
}
