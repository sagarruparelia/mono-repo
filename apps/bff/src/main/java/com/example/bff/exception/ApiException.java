package com.example.bff.exception;

import org.springframework.http.HttpStatusCode;

public class ApiException extends RuntimeException {

    private final String serviceName;
    private final HttpStatusCode statusCode;
    private final String responseBody;

    public ApiException(String serviceName, HttpStatusCode statusCode, String message) {
        super(message);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
        this.responseBody = null;
    }

    public ApiException(String serviceName, HttpStatusCode statusCode, String message, String responseBody) {
        super(message);
        this.serviceName = serviceName;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public ApiException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.statusCode = null;
        this.responseBody = null;
    }

    public String getServiceName() {
        return serviceName;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    @Override
    public String toString() {
        return "ApiException{" +
                "serviceName='" + serviceName + '\'' +
                ", statusCode=" + statusCode +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
