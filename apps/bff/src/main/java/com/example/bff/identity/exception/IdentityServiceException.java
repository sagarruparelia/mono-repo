package com.example.bff.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an identity service API call fails.
 * Used for User Service, Eligibility, and Permissions API failures.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class IdentityServiceException extends RuntimeException {

    private final String serviceName;
    private final int statusCode;

    public IdentityServiceException(String serviceName, String message) {
        super(String.format("%s API error: %s", serviceName, message));
        this.serviceName = serviceName;
        this.statusCode = -1;
    }

    public IdentityServiceException(String serviceName, int statusCode, String message) {
        super(String.format("%s API returned %d: %s", serviceName, statusCode, message));
        this.serviceName = serviceName;
        this.statusCode = statusCode;
    }

    public IdentityServiceException(String serviceName, Throwable cause) {
        super(String.format("%s API error: %s", serviceName, cause.getMessage()), cause);
        this.serviceName = serviceName;
        this.statusCode = -1;
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
