package com.example.bff.common.util;

import com.example.bff.authz.exception.IdentityServiceException;
import org.springframework.lang.NonNull;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Utility methods for retry logic in external service calls.
 * Centralizes retry conditions for consistent behavior.
 */
public final class RetryUtils {

    private RetryUtils() {}

    /**
     * Determines if an exception is retryable based on common patterns.
     * Retryable conditions:
     * - WebClientResponseException with 5xx status
     * - IdentityServiceException with statusCode >= 500
     * - TimeoutException
     *
     * @param throwable the exception to check
     * @return true if the exception is retryable
     */
    public static boolean isRetryable(@NonNull Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }
        if (throwable instanceof IdentityServiceException ex) {
            return ex.getStatusCode() >= 500;
        }
        return throwable instanceof TimeoutException;
    }

    /**
     * Returns a predicate for use with Retry.filter().
     *
     * @return a predicate that returns true for retryable exceptions
     */
    @NonNull
    public static Predicate<Throwable> retryablePredicate() {
        return RetryUtils::isRetryable;
    }

    /**
     * Creates a retryable predicate that also checks custom exception types.
     * The custom checker is called first; if it returns a non-null result,
     * that result is used. Otherwise, falls back to standard checks.
     *
     * @param customChecker a function that returns true/false for custom types, or null to fall back
     * @return a predicate that handles custom and standard exception types
     */
    @NonNull
    public static Predicate<Throwable> retryablePredicateWith(
            @NonNull java.util.function.Function<Throwable, Boolean> customChecker) {
        return throwable -> {
            Boolean customResult = customChecker.apply(throwable);
            if (customResult != null) {
                return customResult;
            }
            return isRetryable(throwable);
        };
    }
}
