package com.example.bff.health.exception;

/**
 * Exception thrown when ECDH Health Data API calls fail.
 */
public class EcdhApiException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public EcdhApiException(int statusCode, String responseBody) {
        super("ECDH API error: status=" + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public EcdhApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.responseBody = null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isRetryable() {
        return statusCode >= 500;
    }
}
