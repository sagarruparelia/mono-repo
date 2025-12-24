package com.example.bff.document.controller;

import com.example.bff.document.model.request.*;
import com.example.bff.document.model.response.*;
import com.example.bff.document.service.DocumentCategoryService;
import com.example.bff.document.service.DocumentService;
import com.example.bff.security.annotation.MfeEnabled;
import com.example.bff.security.annotation.RequiredPersona;
import com.example.bff.security.annotation.ResolvedAuth;
import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.Persona;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentCategoryService categoryService;

    @PostMapping("/upload/initiate")
    @MfeEnabled
    @RequiredPersona(
            value = {Persona.SELF, Persona.DELEGATE, Persona.AGENT, Persona.CASE_WORKER},
            requiredDelegates = {DelegateType.ROI, DelegateType.DAA, DelegateType.RPR}
    )
    public Mono<InitiateUploadResponse> initiateUpload(
            @ResolvedAuth AuthContext auth,
            @Valid @RequestBody InitiateUploadRequest request) {

        log.debug("POST /upload/initiate - user: {}, persona: {}, filename: {}",
                auth.loggedInMemberIdValue(), auth.persona(), request.filename());
        return documentService.initiateUpload(auth, request);
    }

    @PostMapping("/upload/finalize")
    @MfeEnabled
    @RequiredPersona(
            value = {Persona.SELF, Persona.DELEGATE, Persona.AGENT, Persona.CASE_WORKER},
            requiredDelegates = {DelegateType.ROI, DelegateType.DAA, DelegateType.RPR}
    )
    public Mono<FinalizeUploadResponse> finalizeUpload(
            @ResolvedAuth AuthContext auth,
            @Valid @RequestBody FinalizeUploadRequest request) {

        log.debug("POST /upload/finalize - user: {}, persona: {}, uploadId: {}",
                auth.loggedInMemberIdValue(), auth.persona(), request.uploadId());
        return documentService.finalizeUpload(auth, request);
    }

    @PostMapping("/list")
    @MfeEnabled
    @RequiredPersona(
            value = {Persona.SELF, Persona.DELEGATE, Persona.AGENT, Persona.CASE_WORKER},
            requiredDelegates = {DelegateType.ROI, DelegateType.DAA, DelegateType.RPR}
    )
    public Mono<DocumentListResponse> listDocuments(
            @ResolvedAuth AuthContext auth,
            @Valid @RequestBody DocumentListRequest request) {

        log.debug("POST /list - user: {}, persona: {}, page: {}, size: {}",
                auth.loggedInMemberIdValue(), auth.persona(), request.page(), request.size());
        return documentService.listDocuments(auth, request);
    }

    @PostMapping("/download")
    @MfeEnabled
    @RequiredPersona(
            value = {Persona.SELF, Persona.DELEGATE, Persona.AGENT, Persona.CASE_WORKER},
            requiredDelegates = {DelegateType.ROI, DelegateType.DAA, DelegateType.RPR}
    )
    public Mono<DocumentDownloadResponse> getDownloadUrl(
            @ResolvedAuth AuthContext auth,
            @Valid @RequestBody DocumentDownloadRequest request) {

        log.debug("POST /download - user: {}, persona: {}, documentId: {}",
                auth.loggedInMemberIdValue(), auth.persona(), request.documentId());
        return documentService.getDownloadUrl(auth, request);
    }

    @PostMapping("/delete")
    @MfeEnabled
    @RequiredPersona(
            value = {Persona.SELF, Persona.DELEGATE, Persona.AGENT, Persona.CASE_WORKER},
            requiredDelegates = {DelegateType.ROI, DelegateType.DAA, DelegateType.RPR}
    )
    public Mono<DocumentDeleteResponse> deleteDocument(
            @ResolvedAuth AuthContext auth,
            @Valid @RequestBody DocumentDeleteRequest request) {

        log.debug("POST /delete - user: {}, persona: {}, documentId: {}",
                auth.loggedInMemberIdValue(), auth.persona(), request.documentId());
        return documentService.deleteDocument(auth, request);
    }

    @PostMapping("/detail")
    @MfeEnabled
    @RequiredPersona(
            value = {Persona.SELF, Persona.DELEGATE, Persona.AGENT, Persona.CASE_WORKER},
            requiredDelegates = {DelegateType.ROI, DelegateType.DAA, DelegateType.RPR}
    )
    public Mono<DocumentDetailResponse> getDocumentDetail(
            @ResolvedAuth AuthContext auth,
            @Valid @RequestBody DocumentDetailRequest request) {

        log.debug("POST /detail - user: {}, persona: {}, documentId: {}",
                auth.loggedInMemberIdValue(), auth.persona(), request.documentId());
        return documentService.getDocumentDetail(auth, request);
    }

    @GetMapping("/categories")
    public Mono<DocumentCategoryResponse> getCategories() {
        log.debug("GET /categories");
        return categoryService.getActiveCategories()
                .map(categories -> new DocumentCategoryResponse(
                        categories.stream()
                                .map(cat -> new DocumentCategoryResponse.CategoryInfo(
                                        cat.getId(),
                                        cat.getDisplayName(),
                                        cat.getDescription()))
                                .toList()));
    }
}
