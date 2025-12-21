package com.example.bff.document.controller;

import com.example.bff.auth.model.AuthContext;
import com.example.bff.auth.util.AuthContextResolver;
import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.abac.service.AbacAuthorizationService;
import com.example.bff.authz.model.AuthType;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.document.dto.DocumentDto;
import com.example.bff.document.dto.DocumentUploadRequest;
import com.example.bff.document.model.DocumentEntity.DocumentType;
import com.example.bff.document.service.DocumentService;
import com.example.bff.session.service.SessionService;
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
 * API Version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/1.0.0/documents")
@RequiredArgsConstructor
public class DocumentController {

    private static final String SESSION_COOKIE = "BFF_SESSION";
    private static final String ENTERPRISE_ID_HEADER = "X-Enterprise-Id";

    private final DocumentService documentService;
    private final AbacAuthorizationService authorizationService;
    private final SessionService sessionService;

    @GetMapping
    public Mono<ResponseEntity<List<DocumentDto>>> listDocuments(
            @RequestHeader(value = ENTERPRISE_ID_HEADER, required = false) String enterpriseId,
            ServerWebExchange exchange) {

        return resolveEnterpriseId(enterpriseId, exchange)
                .flatMap(memberId -> authorizeAndExecute(exchange, memberId, Action.LIST, "list",
                        () -> documentService.listDocuments(memberId)
                                .collectList()
                                .map(ResponseEntity::ok)))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build()));
    }

    @GetMapping("/{documentId}")
    public Mono<ResponseEntity<DocumentDto>> getDocument(
            @PathVariable String documentId,
            @RequestHeader(value = ENTERPRISE_ID_HEADER, required = false) String enterpriseId,
            ServerWebExchange exchange) {

        return resolveEnterpriseId(enterpriseId, exchange)
                .flatMap(memberId -> authorizeAndExecute(exchange, memberId, Action.VIEW, documentId,
                        () -> documentService.getDocument(memberId, documentId)
                                .map(ResponseEntity::ok)
                                .defaultIfEmpty(ResponseEntity.notFound().build())))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build()));
    }

    @GetMapping("/{documentId}/download")
    public Mono<ResponseEntity<byte[]>> downloadDocument(
            @PathVariable String documentId,
            @RequestHeader(value = ENTERPRISE_ID_HEADER, required = false) String enterpriseId,
            ServerWebExchange exchange) {

        return resolveEnterpriseId(enterpriseId, exchange)
                .flatMap(memberId -> authorizeAndExecute(exchange, memberId, Action.VIEW_SENSITIVE, documentId,
                        () -> documentService.getDocument(memberId, documentId)
                                .flatMap(doc -> documentService.downloadDocument(memberId, documentId)
                                        .map(content -> ResponseEntity.ok()
                                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                                        "attachment; filename=\"" + sanitizeFilename(doc.fileName()) + "\"")
                                                .contentType(MediaType.parseMediaType(doc.contentType()))
                                                .contentLength(content.length)
                                                .body(content)))
                                .defaultIfEmpty(ResponseEntity.notFound().build())))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<DocumentDto>> uploadDocument(
            @RequestHeader(value = ENTERPRISE_ID_HEADER, required = false) String enterpriseId,
            @RequestPart("file") FilePart file,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "documentType", required = false) String documentType,
            ServerWebExchange exchange) {

        DocumentUploadRequest request = new DocumentUploadRequest(
                description,
                parseDocumentType(documentType)
        );

        return resolveEnterpriseId(enterpriseId, exchange)
                .flatMap(memberId -> authorizeUpload(exchange, memberId, file, request))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build()));
    }

    @DeleteMapping("/{documentId}")
    public Mono<ResponseEntity<Void>> deleteDocument(
            @PathVariable String documentId,
            @RequestHeader(value = ENTERPRISE_ID_HEADER, required = false) String enterpriseId,
            ServerWebExchange exchange) {

        return resolveEnterpriseId(enterpriseId, exchange)
                .flatMap(memberId -> authorizeAndExecute(exchange, memberId, Action.DELETE, documentId,
                        () -> documentService.deleteDocument(memberId, documentId)
                                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                                .onErrorResume(IllegalArgumentException.class,
                                        e -> Mono.just(ResponseEntity.notFound().build()))))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build()));
    }

    /**
     * Resolve enterprise ID from header or session context.
     * - PROXY: Uses X-Enterprise-Id header (required)
     * - HSID Self: Uses session's effectiveMemberId
     * - HSID ResponsibleParty: Uses X-Enterprise-Id header, validated against permissions
     */
    private Mono<String> resolveEnterpriseId(String headerEnterpriseId, ServerWebExchange exchange) {
        AuthContext ctx = AuthContextResolver.resolve(exchange).orElse(null);
        if (ctx == null) {
            return Mono.error(new IllegalStateException("No auth context available"));
        }

        // PROXY: Trust header (authZ delegated to consumer)
        if (ctx.isProxy()) {
            if (headerEnterpriseId == null || headerEnterpriseId.isBlank()) {
                return Mono.error(new IllegalArgumentException("X-Enterprise-Id required for proxy auth"));
            }
            return Mono.just(headerEnterpriseId);
        }

        // HSID Self: Use own eid from session
        if (ctx.isSelf()) {
            return Mono.just(ctx.effectiveMemberId());
        }

        // HSID ResponsibleParty: Validate against permission set
        if (ctx.isResponsibleParty()) {
            if (headerEnterpriseId == null || headerEnterpriseId.isBlank()) {
                return Mono.error(new IllegalArgumentException("X-Enterprise-Id required for ResponsibleParty"));
            }

            // Validate eid is in viewable dependents (DAA + RPR)
            return sessionService.getPermissions(ctx.sessionId())
                    .flatMap(permissions -> {
                        List<String> allowed = permissions.getViewableDependentIds();
                        if (allowed.contains(headerEnterpriseId)) {
                            return Mono.just(headerEnterpriseId);
                        }
                        log.warn("ResponsibleParty attempted to access unauthorized member: {}",
                                StringSanitizer.forLog(headerEnterpriseId));
                        return Mono.error(new SecurityException(
                                "No permission to access member: " + headerEnterpriseId));
                    })
                    .switchIfEmpty(Mono.error(new IllegalStateException("Could not load permissions")));
        }

        return Mono.error(new IllegalArgumentException("Invalid persona: " + ctx.persona()));
    }

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
                                        action, StringSanitizer.forLog(documentId), StringSanitizer.forLog(memberId), decision.reason());
                                return Mono.<ResponseEntity<T>>just(buildForbiddenResponse(decision));
                            });
                })
                .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<T>build())));
    }

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
                                        StringSanitizer.forLog(memberId), decision.reason());
                                return Mono.<ResponseEntity<DocumentDto>>just(buildForbiddenResponse(decision));
                            });
                })
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).<DocumentDto>build()));
    }

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
