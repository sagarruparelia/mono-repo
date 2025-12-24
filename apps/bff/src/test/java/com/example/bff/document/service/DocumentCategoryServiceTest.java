package com.example.bff.document.service;

import com.example.bff.client.cache.ClientCache;
import com.example.bff.document.document.DocumentCategoryDoc;
import com.example.bff.document.repository.DocumentCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentCategoryService")
class DocumentCategoryServiceTest {

    @Mock
    private DocumentCategoryRepository repository;

    @Mock
    private ClientCache<List<DocumentCategoryDoc>> cache;

    private DocumentCategoryService service;

    @BeforeEach
    void setUp() {
        service = new DocumentCategoryService(repository, cache);
    }

    @Nested
    @DisplayName("getActiveCategories")
    class GetActiveCategories {

        @Test
        @DisplayName("should return categories from cache")
        void shouldReturnCategoriesFromCache() {
            List<DocumentCategoryDoc> categories = List.of(
                    DocumentCategoryDoc.builder()
                            .id("PRESCRIPTION")
                            .displayName("Prescription")
                            .description("Medicine prescriptions")
                            .active(true)
                            .sortOrder(1)
                            .build(),
                    DocumentCategoryDoc.builder()
                            .id("MARKSHEET")
                            .displayName("Marksheet")
                            .description("Academic records")
                            .active(true)
                            .sortOrder(2)
                            .build()
            );

            // Mock repository even though cache intercepts - needed for eager arg evaluation
            lenient().when(repository.findByActiveTrueOrderBySortOrderAsc())
                    .thenReturn(Flux.empty());
            when(cache.getOrCompute(eq("document:categories:active"), any()))
                    .thenReturn(Mono.just(categories));

            StepVerifier.create(service.getActiveCategories())
                    .assertNext(result -> {
                        assertThat(result).hasSize(2);
                        assertThat(result.get(0).getId()).isEqualTo("PRESCRIPTION");
                        assertThat(result.get(1).getId()).isEqualTo("MARKSHEET");
                    })
                    .verifyComplete();

            verify(cache).getOrCompute(eq("document:categories:active"), any());
        }
    }

    @Nested
    @DisplayName("isValidCategory")
    class IsValidCategory {

        @Test
        @DisplayName("should return true for valid category")
        void shouldReturnTrueForValidCategory() {
            List<DocumentCategoryDoc> categories = List.of(
                    DocumentCategoryDoc.builder()
                            .id("PRESCRIPTION")
                            .displayName("Prescription")
                            .active(true)
                            .build()
            );

            // Mock repository even though cache intercepts - needed for eager arg evaluation
            lenient().when(repository.findByActiveTrueOrderBySortOrderAsc())
                    .thenReturn(Flux.empty());
            when(cache.getOrCompute(eq("document:categories:active"), any()))
                    .thenReturn(Mono.just(categories));

            StepVerifier.create(service.isValidCategory("PRESCRIPTION"))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return false for invalid category")
        void shouldReturnFalseForInvalidCategory() {
            List<DocumentCategoryDoc> categories = List.of(
                    DocumentCategoryDoc.builder()
                            .id("PRESCRIPTION")
                            .displayName("Prescription")
                            .active(true)
                            .build()
            );

            // Mock repository even though cache intercepts - needed for eager arg evaluation
            lenient().when(repository.findByActiveTrueOrderBySortOrderAsc())
                    .thenReturn(Flux.empty());
            when(cache.getOrCompute(eq("document:categories:active"), any()))
                    .thenReturn(Mono.just(categories));

            StepVerifier.create(service.isValidCategory("NONEXISTENT"))
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getCategoryById")
    class GetCategoryById {

        @Test
        @DisplayName("should return category when found")
        void shouldReturnCategoryWhenFound() {
            DocumentCategoryDoc category = DocumentCategoryDoc.builder()
                    .id("PRESCRIPTION")
                    .displayName("Prescription")
                    .description("Medicine prescriptions")
                    .active(true)
                    .build();

            when(repository.findById("PRESCRIPTION"))
                    .thenReturn(Mono.just(category));

            StepVerifier.create(service.getCategoryById("PRESCRIPTION"))
                    .assertNext(result -> {
                        assertThat(result.getId()).isEqualTo("PRESCRIPTION");
                        assertThat(result.getDisplayName()).isEqualTo("Prescription");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmptyWhenNotFound() {
            when(repository.findById("NONEXISTENT"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(service.getCategoryById("NONEXISTENT"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("invalidateCache")
    class InvalidateCache {

        @Test
        @DisplayName("should invalidate the cache key")
        void shouldInvalidateCacheKey() {
            when(cache.invalidate("document:categories:active"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(service.invalidateCache())
                    .verifyComplete();

            verify(cache).invalidate("document:categories:active");
        }
    }
}
