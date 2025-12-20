package com.example.bff.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user has no access to the system.
 * This occurs when the user has no eligibility and no managed members.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class NoAccessException extends RuntimeException {

    private final String hsidUuid;
    private final String reason;

    public NoAccessException(String hsidUuid, String reason) {
        super(String.format("User %s has no access: %s", hsidUuid, reason));
        this.hsidUuid = hsidUuid;
        this.reason = reason;
    }

    public NoAccessException(String message) {
        super(message);
        this.hsidUuid = null;
        this.reason = message;
    }

    public String getHsidUuid() {
        return hsidUuid;
    }

    public String getReason() {
        return reason;
    }
}
