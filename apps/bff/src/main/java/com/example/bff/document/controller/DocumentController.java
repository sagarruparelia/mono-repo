package com.example.bff.document.controller;

import com.example.bff.auth.model.AuthPrincipal;
import com.example.bff.auth.model.DelegateType;
import com.example.bff.auth.model.Persona;
import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.abac.service.AbacAuthorizationService;
import com.example.bff.authz.annotation.RequirePersona;
import com.example.bff.common.util.StringSanitizer;
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

/**
 * REST controller for document management.
 *
 * <p>Uses {@link RequirePersona} for declarative persona-based authorization.
 * Document access requires specific delegate permissions for DELEGATE persona.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/1.0.0/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final AbacAuthorizationService authorizationService;

    /**
     * List all documents for the authenticated member.
     */
    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @GetMapping
    public Mono<ResponseEntity<List<DocumentDto>>> listDocuments(
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        return authorizeAndExecute(principal, exchange, Action.LIST, "list",
                () -> documentService.listDocuments(principal.enterpriseId())
                        .collectList()
                        .map(ResponseEntity::ok));
    }

    /**
     * Get a specific document.
     */
    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @GetMapping("/{documentId}")
    public Mono<ResponseEntity<DocumentDto>> getDocument(
            @PathVariable String documentId,
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        return authorizeAndExecute(principal, exchange, Action.VIEW, documentId,
                () -> documentService.getDocument(principal.enterpriseId(), documentId)
                        .map(ResponseEntity::ok)
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
    }

    /**
     * Download a document (requires sensitive access for DELEGATE).
     */
    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT, Persona.CONFIG_SPECIALIST},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR, DelegateType.ROI})
    @GetMapping("/{documentId}/download")
    public Mono<ResponseEntity<byte[]>> downloadDocument(
            @PathVariable String documentId,
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        return authorizeAndExecute(principal, exchange, Action.VIEW_SENSITIVE, documentId,
                () -> documentService.getDocument(principal.enterpriseId(), documentId)
                        .flatMap(doc -> documentService.downloadDocument(principal.enterpriseId(), documentId)
                                .map(content -> ResponseEntity.ok()
                                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                                "attachment; filename=\"" + sanitizeFilename(doc.fileName()) + "\"")
                                        .contentType(MediaType.parseMediaType(doc.contentType()))
                                        .contentLength(content.length)
                                        .body(content)))
                        .defaultIfEmpty(ResponseEntity.notFound().build()));
    }

    /**
     * Upload a new document.
     */
    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<DocumentDto>> uploadDocument(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "documentType", required = false) String documentType,
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        DocumentUploadRequest request = new DocumentUploadRequest(
                description,
                parseDocumentType(documentType)
        );

        return authorizeUpload(principal, exchange, file, request);
    }

    /**
     * Delete a document.
     */
    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @DeleteMapping("/{documentId}")
    public Mono<ResponseEntity<Void>> deleteDocument(
            @PathVariable String documentId,
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        return authorizeAndExecute(principal, exchange, Action.DELETE, documentId,
                () -> documentService.deleteDocument(principal.enterpriseId(), documentId)
                        .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                        .onErrorResume(IllegalArgumentException.class,
                                e -> Mono.just(ResponseEntity.notFound().build())));
    }

    /**
     * Authorize and execute an operation.
     * - PROXY: Skip ABAC (authorization delegated to consumer)
     * - HSID: Apply ABAC authorization using SubjectAttributes
     */
    private <T> Mono<ResponseEntity<T>> authorizeAndExecute(
            AuthPrincipal principal,
            ServerWebExchange exchange,
            Action action,
            String documentId,
            java.util.function.Supplier<Mono<ResponseEntity<T>>> operation) {

        log.debug("Document access: persona={}, enterpriseId={}, action={}, documentId={}",
                principal.persona(), StringSanitizer.forLog(principal.enterpriseId()),
                action, StringSanitizer.forLog(documentId));

        // PROXY: Skip ABAC - authorization delegated to consumer
        if (principal.isProxyAuth()) {
            log.debug("Proxy auth - skipping ABAC, authZ delegated to consumer");
            return operation.get();
        }

        // HSID: Apply ABAC authorization
        SubjectAttributes subject = SubjectAttributes.fromPrincipal(principal);
        ResourceAttributes resource = ResourceAttributes.document(documentId, principal.enterpriseId());

        return authorizationService.authorize(subject, resource, action, exchange.getRequest())
                .<ResponseEntity<T>>flatMap(decision -> {
                    if (decision.isAllowed()) {
                        return operation.get();
                    }
                    log.warn("Authorization denied for action {} on document {} for member {}: {}",
                            action, StringSanitizer.forLog(documentId),
                            StringSanitizer.forLog(principal.enterpriseId()), decision.reason());
                    return Mono.just(buildForbiddenResponse(decision));
                })
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
    }

    private Mono<ResponseEntity<DocumentDto>> authorizeUpload(
            AuthPrincipal principal,
            ServerWebExchange exchange,
            FilePart file,
            DocumentUploadRequest request) {

        log.debug("Document upload: persona={}, enterpriseId={}",
                principal.persona(), StringSanitizer.forLog(principal.enterpriseId()));

        // PROXY: Skip ABAC - authorization delegated to consumer
        if (principal.isProxyAuth()) {
            log.debug("Proxy auth - skipping ABAC for upload");
            return documentService.uploadDocument(
                            principal.enterpriseId(), file, request,
                            principal.loggedInMemberIdValue(), principal.persona().toLegacy())
                    .map(doc -> ResponseEntity.status(HttpStatus.CREATED).body(doc))
                    .onErrorResume(IllegalArgumentException.class,
                            e -> Mono.just(ResponseEntity.badRequest().body(null)));
        }

        // HSID: Apply ABAC authorization
        SubjectAttributes subject = SubjectAttributes.fromPrincipal(principal);
        ResourceAttributes resource = ResourceAttributes.document("new", principal.enterpriseId());

        return authorizationService.authorize(subject, resource, Action.UPLOAD, exchange.getRequest())
                .flatMap(decision -> {
                    if (decision.isAllowed()) {
                        return documentService.uploadDocument(
                                        principal.enterpriseId(), file, request,
                                        principal.loggedInMemberIdValue(), principal.persona().toLegacy())
                                .map(doc -> ResponseEntity.status(HttpStatus.CREATED).body(doc))
                                .onErrorResume(IllegalArgumentException.class,
                                        e -> Mono.just(ResponseEntity.badRequest().body(null)));
                    }
                    log.warn("Authorization denied for upload to member {}: {}",
                            StringSanitizer.forLog(principal.enterpriseId()), decision.reason());
                    return Mono.<ResponseEntity<DocumentDto>>just(buildForbiddenResponse(decision));
                })
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
    }

    private <T> ResponseEntity<T> buildForbiddenResponse(PolicyDecision decision) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .header("X-Policy-Id", decision.policyId())
                .header("X-Policy-Reason", sanitizeHeader(decision.reason()))
                .build();
    }

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

    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "download";
        }
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String sanitizeHeader(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replace("\n", " ").replace("\r", " ");
        return sanitized.substring(0, Math.min(sanitized.length(), 200));
    }
}
