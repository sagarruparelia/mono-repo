package com.example.bff.common.exception;

public class SessionBindingException extends RuntimeException {

    private final String reason;

    public SessionBindingException(String reason) {
        super("Session binding validation failed: " + reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
