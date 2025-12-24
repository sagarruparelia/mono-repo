package com.example.bff.document.service;

import com.example.bff.client.cache.ClientCache;
import com.example.bff.document.document.DocumentCategoryDoc;
import com.example.bff.document.repository.DocumentCategoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
public class DocumentCategoryService {

    private static final String CACHE_KEY = "document:categories:active";

    private final DocumentCategoryRepository repository;
    private final ClientCache<List<DocumentCategoryDoc>> cache;

    public DocumentCategoryService(DocumentCategoryRepository repository,
                                   ClientCache<List<DocumentCategoryDoc>> cache) {
        this.repository = repository;
        this.cache = cache;
    }

    public Mono<List<DocumentCategoryDoc>> getActiveCategories() {
        return cache.getOrCompute(CACHE_KEY,
                repository.findByActiveTrueOrderBySortOrderAsc()
                        .collectList()
                        .doOnSuccess(categories ->
                                log.debug("Loaded {} active categories from database", categories.size()))
        );
    }

    public Mono<Boolean> isValidCategory(String categoryId) {
        return getActiveCategories()
                .map(categories -> categories.stream()
                        .anyMatch(c -> c.getId().equals(categoryId)));
    }

    public Mono<DocumentCategoryDoc> getCategoryById(String categoryId) {
        return repository.findById(categoryId);
    }

    public Mono<Void> invalidateCache() {
        return cache.invalidate(CACHE_KEY)
                .doOnSuccess(v -> log.debug("Invalidated category cache"));
    }
}
