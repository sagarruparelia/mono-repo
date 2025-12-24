package com.example.bff.util;

import com.example.bff.health.model.PaginationRequest;

/**
 * Test builder for PaginationRequest.
 * Provides convenient methods to create PaginationRequest instances for testing.
 */
public class PaginationRequestTestBuilder {

    private String enterpriseId = null;
    private int page = 0;
    private int size = 20;

    public static PaginationRequestTestBuilder aPaginationRequest() {
        return new PaginationRequestTestBuilder();
    }

    public static PaginationRequest aDefaultRequest() {
        return aPaginationRequest().build();
    }

    public static PaginationRequest aRequestForEnterpriseId(String enterpriseId) {
        return aPaginationRequest()
                .withEnterpriseId(enterpriseId)
                .build();
    }

    public static PaginationRequest aRequestWithPage(int page) {
        return aPaginationRequest()
                .withPage(page)
                .build();
    }

    public static PaginationRequest aRequestWithSize(int size) {
        return aPaginationRequest()
                .withSize(size)
                .build();
    }

    public PaginationRequestTestBuilder withEnterpriseId(String enterpriseId) {
        this.enterpriseId = enterpriseId;
        return this;
    }

    public PaginationRequestTestBuilder withPage(int page) {
        this.page = page;
        return this;
    }

    public PaginationRequestTestBuilder withSize(int size) {
        this.size = size;
        return this;
    }

    public PaginationRequestTestBuilder withMaxSize() {
        this.size = 100;
        return this;
    }

    public PaginationRequestTestBuilder withInvalidPage() {
        this.page = -1;
        return this;
    }

    public PaginationRequestTestBuilder withInvalidSize() {
        this.size = 0;
        return this;
    }

    public PaginationRequestTestBuilder withOversizedPage() {
        this.size = 101;
        return this;
    }

    public PaginationRequest build() {
        return new PaginationRequest(enterpriseId, page, size);
    }
}
