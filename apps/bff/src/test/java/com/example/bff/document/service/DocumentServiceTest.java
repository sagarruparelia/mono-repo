package com.example.bff.document.service;

import com.example.bff.config.BffProperties;
import com.example.bff.document.client.S3StorageClient;
import com.example.bff.document.document.DocumentCategoryDoc;
import com.example.bff.document.document.DocumentMetadataDoc;
import com.example.bff.document.document.TempUploadDoc;
import com.example.bff.document.model.DocumentStatus;
import com.example.bff.document.model.ScanStatus;
import com.example.bff.document.model.UploadStatus;
import com.example.bff.document.model.request.*;
import com.example.bff.document.repository.DocumentMetadataRepository;
import com.example.bff.document.repository.TempUploadRepository;
import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.MemberIdType;
import com.example.bff.security.context.Persona;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static com.example.bff.util.AuthContextTestBuilder.anAuthContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService")
class DocumentServiceTest {

    @Mock
    private S3StorageClient s3Client;

    @Mock
    private DocumentMetadataRepository documentRepository;

    @Mock
    private TempUploadRepository tempUploadRepository;

    @Mock
    private DocumentCategoryService categoryService;

    @Mock
    private DocumentAccessValidator accessValidator;

    private BffProperties properties;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        properties = new BffProperties();
        BffProperties.Document docConfig = properties.getDocument();
        docConfig.setS3Bucket("test-bucket");
        docConfig.setMaxFileSizeMb(25);
        docConfig.setMaxConcurrentUploads(4);
        docConfig.setPresignedUploadTtlMinutes(15);
        docConfig.setPresignedDownloadTtlMinutes(5);
        docConfig.setTempExpiryHours(4);
        docConfig.setAllowedContentTypes(List.of(
                "application/pdf",
                "image/jpeg",
                "image/png"
        ));

        documentService = new DocumentService(
                s3Client,
                documentRepository,
                tempUploadRepository,
                categoryService,
                accessValidator,
                properties
        );
    }

    private AuthContext selfAuthContext() {
        return anAuthContext()
                .withPersona(Persona.SELF)
                .withEnterpriseId("ENT-001")
                .withLoggedInMemberIdValue("user-123")
                .withLoggedInMemberIdType(MemberIdType.HSID)
                .build();
    }

    private AuthContext delegateAuthContext() {
        return anAuthContext()
                .withPersona(Persona.DELEGATE)
                .withEnterpriseId("MANAGED-ENT-001")
                .withLoggedInMemberIdValue("delegate-123")
                .withLoggedInMemberIdType(MemberIdType.HSID)
                .withActiveDelegateTypes(Set.of(DelegateType.ROI, DelegateType.DAA, DelegateType.RPR))
                .build();
    }

    @Nested
    @DisplayName("initiateUpload")
    class InitiateUpload {

        @Test
        @DisplayName("should create temp upload and return presigned URL")
        void shouldCreateTempUploadAndReturnPresignedUrl() {
            AuthContext auth = selfAuthContext();
            InitiateUploadRequest request = new InitiateUploadRequest(null, "test.pdf", "application/pdf", 1024L);

            when(accessValidator.validateUpload(any(), eq("ENT-001")))
                    .thenReturn(Mono.empty());
            when(tempUploadRepository.countByUploaderIdAndUploadStatus(eq("user-123"), eq(UploadStatus.PENDING)))
                    .thenReturn(Mono.just(0L));
            when(s3Client.generatePresignedUploadUrl(anyString(), eq("application/pdf"), eq(1024L)))
                    .thenReturn(Mono.just(S3StorageClient.PresignedUploadResult.builder()
                            .url("https://s3.amazonaws.com/presigned-url")
                            .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                            .build()));
            when(tempUploadRepository.save(any(TempUploadDoc.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            StepVerifier.create(documentService.initiateUpload(auth, request))
                    .assertNext(response -> {
                        assertThat(response.uploadId()).isNotNull();
                        assertThat(response.presignedUrl()).isEqualTo("https://s3.amazonaws.com/presigned-url");
                        assertThat(response.requiredContentType()).isEqualTo("application/pdf");
                    })
                    .verifyComplete();

            ArgumentCaptor<TempUploadDoc> captor = ArgumentCaptor.forClass(TempUploadDoc.class);
            verify(tempUploadRepository).save(captor.capture());
            TempUploadDoc saved = captor.getValue();
            assertThat(saved.getUploaderId()).isEqualTo("user-123");
            assertThat(saved.getTargetOwnerEid()).isEqualTo("ENT-001");
            assertThat(saved.getUploadStatus()).isEqualTo(UploadStatus.PENDING);
        }

        @Test
        @DisplayName("should reject invalid content type")
        void shouldRejectInvalidContentType() {
            AuthContext auth = selfAuthContext();
            InitiateUploadRequest request = new InitiateUploadRequest(null, "test.exe", "application/x-msdownload", 1024L);

            when(accessValidator.validateUpload(any(), anyString()))
                    .thenReturn(Mono.empty());
            // Need to mock this even though it won't be subscribed to (eager evaluation of chain)
            lenient().when(tempUploadRepository.countByUploaderIdAndUploadStatus(anyString(), any()))
                    .thenReturn(Mono.just(0L));

            StepVerifier.create(documentService.initiateUpload(auth, request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(400);
                        assertThat(error.getMessage()).contains("Content type not allowed");
                    })
                    .verify();
        }

        @Test
        @DisplayName("should reject when concurrent upload limit reached")
        void shouldRejectWhenConcurrentUploadLimitReached() {
            AuthContext auth = selfAuthContext();
            InitiateUploadRequest request = new InitiateUploadRequest(null, "test.pdf", "application/pdf", 1024L);

            when(accessValidator.validateUpload(any(), anyString()))
                    .thenReturn(Mono.empty());
            when(tempUploadRepository.countByUploaderIdAndUploadStatus(eq("user-123"), eq(UploadStatus.PENDING)))
                    .thenReturn(Mono.just(4L));

            StepVerifier.create(documentService.initiateUpload(auth, request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(429);
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("finalizeUpload")
    class FinalizeUpload {

        private TempUploadDoc validTempUpload;

        @BeforeEach
        void setUp() {
            validTempUpload = TempUploadDoc.builder()
                    .id("upload-123")
                    .s3Key("temp/user-123/upload-123/test.pdf")
                    .uploaderId("user-123")
                    .targetOwnerEid("ENT-001")
                    .originalFilename("test.pdf")
                    .contentType("application/pdf")
                    .expectedFileSizeBytes(1024L)
                    .uploadStatus(UploadStatus.PENDING)
                    .expiresAt(Instant.now().plus(4, ChronoUnit.HOURS))
                    .build();
        }

        @Test
        @DisplayName("should move file and create document record")
        void shouldMoveFileAndCreateDocumentRecord() {
            AuthContext auth = selfAuthContext();
            FinalizeUploadRequest request = new FinalizeUploadRequest(null, "upload-123", "PRESCRIPTION", "My Prescription", null);

            when(accessValidator.validateUpload(any(), eq("ENT-001")))
                    .thenReturn(Mono.empty());
            when(categoryService.isValidCategory("PRESCRIPTION"))
                    .thenReturn(Mono.just(true));
            when(tempUploadRepository.findById("upload-123"))
                    .thenReturn(Mono.just(validTempUpload));
            when(s3Client.checkObjectExists(anyString()))
                    .thenReturn(Mono.just(true));
            when(s3Client.getBucket())
                    .thenReturn("test-bucket");
            when(s3Client.moveObject(anyString(), anyString()))
                    .thenReturn(Mono.empty());
            when(documentRepository.save(any(DocumentMetadataDoc.class)))
                    .thenAnswer(invocation -> {
                        DocumentMetadataDoc doc = invocation.getArgument(0);
                        return Mono.just(doc);
                    });
            when(tempUploadRepository.save(any(TempUploadDoc.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            StepVerifier.create(documentService.finalizeUpload(auth, request))
                    .assertNext(response -> {
                        assertThat(response.documentId()).isNotNull();
                        assertThat(response.filename()).isEqualTo("test.pdf");
                        assertThat(response.category()).isEqualTo("PRESCRIPTION");
                        assertThat(response.status()).isEqualTo("PENDING_SCAN");
                    })
                    .verifyComplete();

            ArgumentCaptor<DocumentMetadataDoc> captor = ArgumentCaptor.forClass(DocumentMetadataDoc.class);
            verify(documentRepository).save(captor.capture());
            DocumentMetadataDoc saved = captor.getValue();
            assertThat(saved.getOwnerEnterpriseId()).isEqualTo("ENT-001");
            assertThat(saved.getUploaderId()).isEqualTo("user-123");
            assertThat(saved.getStatus()).isEqualTo(DocumentStatus.PENDING_SCAN);
            assertThat(saved.getScanStatus()).isEqualTo(ScanStatus.NOT_SCANNED);
        }

        @Test
        @DisplayName("should reject upload not found")
        void shouldRejectUploadNotFound() {
            AuthContext auth = selfAuthContext();
            FinalizeUploadRequest request = new FinalizeUploadRequest(null, "nonexistent-123", "PRESCRIPTION", null, null);

            when(accessValidator.validateUpload(any(), anyString()))
                    .thenReturn(Mono.empty());
            when(categoryService.isValidCategory(anyString()))
                    .thenReturn(Mono.just(true));
            when(tempUploadRepository.findById("nonexistent-123"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(documentService.finalizeUpload(auth, request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404);
                    })
                    .verify();
        }

        @Test
        @DisplayName("should reject invalid category")
        void shouldRejectInvalidCategory() {
            AuthContext auth = selfAuthContext();
            FinalizeUploadRequest request = new FinalizeUploadRequest(null, "upload-123", "INVALID", null, null);

            when(accessValidator.validateUpload(any(), anyString()))
                    .thenReturn(Mono.empty());
            when(categoryService.isValidCategory("INVALID"))
                    .thenReturn(Mono.just(false));
            // Need to mock this even though it won't be subscribed to (eager evaluation of chain)
            lenient().when(tempUploadRepository.findById(anyString()))
                    .thenReturn(Mono.empty());

            StepVerifier.create(documentService.finalizeUpload(auth, request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(400);
                        assertThat(error.getMessage()).contains("Invalid category");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("getDownloadUrl")
    class GetDownloadUrl {

        @Test
        @DisplayName("should return presigned URL when scan is CLEAN")
        void shouldReturnPresignedUrlWhenScanIsClean() {
            AuthContext auth = selfAuthContext();
            DocumentDownloadRequest request = new DocumentDownloadRequest(null, "doc-123");

            DocumentMetadataDoc doc = DocumentMetadataDoc.builder()
                    .id("doc-123")
                    .ownerEnterpriseId("ENT-001")
                    .s3Key("docs/ENT-001/doc-123/test.pdf")
                    .originalFilename("test.pdf")
                    .contentType("application/pdf")
                    .fileSizeBytes(1024L)
                    .status(DocumentStatus.ACTIVE)
                    .scanStatus(ScanStatus.CLEAN)
                    .build();

            when(accessValidator.validateAccess(any(), eq("ENT-001")))
                    .thenReturn(Mono.empty());
            when(documentRepository.findById("doc-123"))
                    .thenReturn(Mono.just(doc));
            when(s3Client.generatePresignedDownloadUrl(anyString(), eq("test.pdf")))
                    .thenReturn(Mono.just(S3StorageClient.PresignedDownloadResult.builder()
                            .url("https://s3.amazonaws.com/download-url")
                            .expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES))
                            .build()));

            StepVerifier.create(documentService.getDownloadUrl(auth, request))
                    .assertNext(response -> {
                        assertThat(response.presignedUrl()).isEqualTo("https://s3.amazonaws.com/download-url");
                        assertThat(response.filename()).isEqualTo("test.pdf");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should block download when scan is NOT_SCANNED")
        void shouldBlockDownloadWhenScanNotScanned() {
            AuthContext auth = selfAuthContext();
            DocumentDownloadRequest request = new DocumentDownloadRequest(null, "doc-123");

            DocumentMetadataDoc doc = DocumentMetadataDoc.builder()
                    .id("doc-123")
                    .ownerEnterpriseId("ENT-001")
                    .status(DocumentStatus.ACTIVE)
                    .scanStatus(ScanStatus.NOT_SCANNED)
                    .build();

            when(accessValidator.validateAccess(any(), anyString()))
                    .thenReturn(Mono.empty());
            when(documentRepository.findById("doc-123"))
                    .thenReturn(Mono.just(doc));

            StepVerifier.create(documentService.getDownloadUrl(auth, request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(409);
                        assertThat(error.getMessage()).contains("being processed");
                    })
                    .verify();
        }

        @Test
        @DisplayName("should block download when scan is INFECTED")
        void shouldBlockDownloadWhenScanInfected() {
            AuthContext auth = selfAuthContext();
            DocumentDownloadRequest request = new DocumentDownloadRequest(null, "doc-123");

            DocumentMetadataDoc doc = DocumentMetadataDoc.builder()
                    .id("doc-123")
                    .ownerEnterpriseId("ENT-001")
                    .status(DocumentStatus.ACTIVE)
                    .scanStatus(ScanStatus.INFECTED)
                    .build();

            when(accessValidator.validateAccess(any(), anyString()))
                    .thenReturn(Mono.empty());
            when(documentRepository.findById("doc-123"))
                    .thenReturn(Mono.just(doc));

            StepVerifier.create(documentService.getDownloadUrl(auth, request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(403);
                        assertThat(error.getMessage()).contains("failed security scan");
                    })
                    .verify();
        }

        @Test
        @DisplayName("should reject access to deleted document")
        void shouldRejectAccessToDeletedDocument() {
            AuthContext auth = selfAuthContext();
            DocumentDownloadRequest request = new DocumentDownloadRequest(null, "doc-123");

            DocumentMetadataDoc doc = DocumentMetadataDoc.builder()
                    .id("doc-123")
                    .ownerEnterpriseId("ENT-001")
                    .status(DocumentStatus.DELETED)
                    .scanStatus(ScanStatus.CLEAN)
                    .build();

            when(accessValidator.validateAccess(any(), anyString()))
                    .thenReturn(Mono.empty());
            when(documentRepository.findById("doc-123"))
                    .thenReturn(Mono.just(doc));

            StepVerifier.create(documentService.getDownloadUrl(auth, request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(410);
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("deleteDocument")
    class DeleteDocument {

        @Test
        @DisplayName("should soft-delete document")
        void shouldSoftDeleteDocument() {
            AuthContext auth = selfAuthContext();
            DocumentDeleteRequest request = new DocumentDeleteRequest(null, "doc-123");

            DocumentMetadataDoc doc = DocumentMetadataDoc.builder()
                    .id("doc-123")
                    .ownerEnterpriseId("ENT-001")
                    .status(DocumentStatus.ACTIVE)
                    .build();

            when(accessValidator.validateAccess(any(), eq("ENT-001")))
                    .thenReturn(Mono.empty());
            when(documentRepository.findById("doc-123"))
                    .thenReturn(Mono.just(doc));
            when(documentRepository.save(any(DocumentMetadataDoc.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            StepVerifier.create(documentService.deleteDocument(auth, request))
                    .assertNext(response -> {
                        assertThat(response.success()).isTrue();
                        assertThat(response.documentId()).isEqualTo("doc-123");
                    })
                    .verifyComplete();

            ArgumentCaptor<DocumentMetadataDoc> captor = ArgumentCaptor.forClass(DocumentMetadataDoc.class);
            verify(documentRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(DocumentStatus.DELETED);
        }

        @Test
        @DisplayName("should reject already deleted document")
        void shouldRejectAlreadyDeletedDocument() {
            AuthContext auth = selfAuthContext();
            DocumentDeleteRequest request = new DocumentDeleteRequest(null, "doc-123");

            DocumentMetadataDoc doc = DocumentMetadataDoc.builder()
                    .id("doc-123")
                    .ownerEnterpriseId("ENT-001")
                    .status(DocumentStatus.DELETED)
                    .build();

            when(accessValidator.validateAccess(any(), anyString()))
                    .thenReturn(Mono.empty());
            when(documentRepository.findById("doc-123"))
                    .thenReturn(Mono.just(doc));

            StepVerifier.create(documentService.deleteDocument(auth, request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(410);
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("listDocuments")
    class ListDocuments {

        @Test
        @DisplayName("should return paginated documents")
        void shouldReturnPaginatedDocuments() {
            AuthContext auth = selfAuthContext();
            DocumentListRequest request = new DocumentListRequest(null, 0, 10, null);

            List<DocumentCategoryDoc> categories = List.of(
                    DocumentCategoryDoc.builder().id("PRESCRIPTION").displayName("Prescription").build()
            );

            DocumentMetadataDoc doc1 = DocumentMetadataDoc.builder()
                    .id("doc-1")
                    .originalFilename("test1.pdf")
                    .category("PRESCRIPTION")
                    .status(DocumentStatus.ACTIVE)
                    .scanStatus(ScanStatus.CLEAN)
                    .build();

            when(accessValidator.validateAccess(any(), eq("ENT-001")))
                    .thenReturn(Mono.empty());
            when(categoryService.getActiveCategories())
                    .thenReturn(Mono.just(categories));
            when(documentRepository.findByOwnerEnterpriseIdAndStatus(eq("ENT-001"), eq(DocumentStatus.ACTIVE), any()))
                    .thenReturn(Flux.just(doc1));
            when(documentRepository.countByOwnerEnterpriseIdAndStatus(eq("ENT-001"), eq(DocumentStatus.ACTIVE)))
                    .thenReturn(Mono.just(1L));

            StepVerifier.create(documentService.listDocuments(auth, request))
                    .assertNext(response -> {
                        assertThat(response.documents()).hasSize(1);
                        assertThat(response.documents().get(0).categoryDisplayName()).isEqualTo("Prescription");
                        assertThat(response.totalRecords()).isEqualTo(1);
                        assertThat(response.page()).isEqualTo(0);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should filter by category when provided")
        void shouldFilterByCategory() {
            AuthContext auth = selfAuthContext();
            DocumentListRequest request = new DocumentListRequest(null, 0, 10, "PRESCRIPTION");

            when(accessValidator.validateAccess(any(), anyString()))
                    .thenReturn(Mono.empty());
            when(categoryService.getActiveCategories())
                    .thenReturn(Mono.just(List.of()));
            when(documentRepository.findByOwnerEnterpriseIdAndStatusAndCategory(
                    eq("ENT-001"), eq(DocumentStatus.ACTIVE), eq("PRESCRIPTION"), any()))
                    .thenReturn(Flux.empty());
            when(documentRepository.countByOwnerEnterpriseIdAndStatusAndCategory(
                    eq("ENT-001"), eq(DocumentStatus.ACTIVE), eq("PRESCRIPTION")))
                    .thenReturn(Mono.just(0L));

            StepVerifier.create(documentService.listDocuments(auth, request))
                    .assertNext(response -> {
                        assertThat(response.documents()).isEmpty();
                        assertThat(response.totalRecords()).isEqualTo(0);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getDocumentDetail")
    class GetDocumentDetail {

        @Test
        @DisplayName("should return document details")
        void shouldReturnDocumentDetails() {
            AuthContext auth = selfAuthContext();
            DocumentDetailRequest request = new DocumentDetailRequest(null, "doc-123");

            DocumentMetadataDoc doc = DocumentMetadataDoc.builder()
                    .id("doc-123")
                    .ownerEnterpriseId("ENT-001")
                    .originalFilename("test.pdf")
                    .contentType("application/pdf")
                    .fileSizeBytes(1024L)
                    .category("PRESCRIPTION")
                    .title("My Prescription")
                    .description("Test description")
                    .status(DocumentStatus.ACTIVE)
                    .scanStatus(ScanStatus.CLEAN)
                    .uploaderId("user-123")
                    .uploaderIdType(MemberIdType.HSID)
                    .uploaderPersona(Persona.SELF)
                    .build();

            DocumentCategoryDoc category = DocumentCategoryDoc.builder()
                    .id("PRESCRIPTION")
                    .displayName("Prescription")
                    .build();

            when(accessValidator.validateAccess(any(), eq("ENT-001")))
                    .thenReturn(Mono.empty());
            when(documentRepository.findById("doc-123"))
                    .thenReturn(Mono.just(doc));
            when(categoryService.getCategoryById("PRESCRIPTION"))
                    .thenReturn(Mono.just(category));

            StepVerifier.create(documentService.getDocumentDetail(auth, request))
                    .assertNext(response -> {
                        assertThat(response.documentId()).isEqualTo("doc-123");
                        assertThat(response.filename()).isEqualTo("test.pdf");
                        assertThat(response.categoryDisplayName()).isEqualTo("Prescription");
                        assertThat(response.uploader().uploaderId()).isEqualTo("user-123");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return 404 for non-existent document")
        void shouldReturn404ForNonExistentDocument() {
            AuthContext auth = selfAuthContext();
            DocumentDetailRequest request = new DocumentDetailRequest(null, "nonexistent-123");

            when(accessValidator.validateAccess(any(), anyString()))
                    .thenReturn(Mono.empty());
            when(documentRepository.findById("nonexistent-123"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(documentService.getDocumentDetail(auth, request))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(ResponseStatusException.class);
                        assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404);
                    })
                    .verify();
        }
    }
}
