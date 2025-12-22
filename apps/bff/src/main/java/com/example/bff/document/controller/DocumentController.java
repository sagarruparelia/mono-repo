package com.example.bff.document.controller;

import com.example.bff.auth.model.AuthPrincipal;
import com.example.bff.auth.model.DelegateType;
import com.example.bff.auth.model.Persona;
import com.example.bff.authz.annotation.RequirePersona;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.document.dto.DocumentDto;
import com.example.bff.document.dto.DocumentIdRequest;
import com.example.bff.document.dto.DocumentUploadRequest;
import com.example.bff.document.model.DocumentEntity.DocumentType;
import com.example.bff.document.service.DocumentService;
import jakarta.validation.Valid;
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

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private static final String VALIDATED_ENTERPRISE_ID = "VALIDATED_ENTERPRISE_ID";

    private final DocumentService documentService;

    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @PostMapping("/list")
    public Mono<ResponseEntity<List<DocumentDto>>> listDocuments(
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Listing documents for enterpriseId={}", StringSanitizer.forLog(enterpriseId));

        return documentService.listDocuments(enterpriseId)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @PostMapping("/get")
    public Mono<ResponseEntity<DocumentDto>> getDocument(
            @Valid @RequestBody DocumentIdRequest request,
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Getting document {} for enterpriseId={}",
                StringSanitizer.forLog(request.documentId()), StringSanitizer.forLog(enterpriseId));

        return documentService.getDocument(enterpriseId, request.documentId())
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT, Persona.CONFIG_SPECIALIST},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR, DelegateType.ROI})
    @PostMapping("/download")
    public Mono<ResponseEntity<byte[]>> downloadDocument(
            @Valid @RequestBody DocumentIdRequest request,
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Downloading document {} for enterpriseId={}",
                StringSanitizer.forLog(request.documentId()), StringSanitizer.forLog(enterpriseId));

        return documentService.getDocument(enterpriseId, request.documentId())
                .flatMap(doc -> documentService.downloadDocument(enterpriseId, request.documentId())
                        .map(content -> ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION,
                                        "attachment; filename=\"" + sanitizeFilename(doc.fileName()) + "\"")
                                .contentType(MediaType.parseMediaType(doc.contentType()))
                                .contentLength(content.length)
                                .body(content)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER, Persona.AGENT},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<DocumentDto>> uploadDocument(
            @RequestPart("file") FilePart file,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "documentType", required = false) String documentType,
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Uploading document for enterpriseId={}", StringSanitizer.forLog(enterpriseId));

        DocumentUploadRequest request = new DocumentUploadRequest(
                description,
                parseDocumentType(documentType)
        );

        return documentService.uploadDocument(
                        enterpriseId, file, request,
                        principal.loggedInMemberIdValue(), principal.persona().toLegacy())
                .map(doc -> ResponseEntity.status(HttpStatus.CREATED).body(doc))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.badRequest().body(null)));
    }

    @RequirePersona(value = {Persona.INDIVIDUAL_SELF, Persona.DELEGATE, Persona.CASE_WORKER},
            requiredDelegates = {DelegateType.DAA, DelegateType.RPR})
    @PostMapping("/delete")
    public Mono<ResponseEntity<Void>> deleteDocument(
            @Valid @RequestBody DocumentIdRequest request,
            AuthPrincipal principal,
            ServerWebExchange exchange) {

        String enterpriseId = getTargetEnterpriseId(exchange, principal);
        log.debug("Deleting document {} for enterpriseId={}",
                StringSanitizer.forLog(request.documentId()), StringSanitizer.forLog(enterpriseId));

        return documentService.deleteDocument(enterpriseId, request.documentId())
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    private String getTargetEnterpriseId(ServerWebExchange exchange, AuthPrincipal principal) {
        String validatedId = exchange.getAttribute(VALIDATED_ENTERPRISE_ID);
        if (validatedId != null && !validatedId.isBlank()) {
            return validatedId;
        }
        return principal.enterpriseId();
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
}
