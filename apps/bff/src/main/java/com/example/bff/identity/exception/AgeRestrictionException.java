package com.example.bff.identity.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user doesn't meet the minimum age requirement.
 * Minimum age is 13 years to access the system.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AgeRestrictionException extends RuntimeException {

    private final int userAge;
    private final int minimumAge;

    public AgeRestrictionException(int userAge, int minimumAge) {
        super(String.format("User age %d is below minimum required age %d", userAge, minimumAge));
        this.userAge = userAge;
        this.minimumAge = minimumAge;
    }

    public AgeRestrictionException(String message) {
        super(message);
        this.userAge = -1;
        this.minimumAge = -1;
    }

    public int getUserAge() {
        return userAge;
    }

    public int getMinimumAge() {
        return minimumAge;
    }
}
