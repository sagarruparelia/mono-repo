package com.example.bff.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Centralized service for recording business-specific metrics.
 * Uses bounded tag values to prevent high-cardinality metric explosion.
 */
@Component
public class BusinessMetrics {

    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_FAILURE = "failure";
    private static final String TAG_UNKNOWN = "unknown";
    private static final int MAX_TAG_LENGTH = 50;

    private final MeterRegistry registry;

    private final Counter authLoginSuccess;
    private final Counter authLoginFailure;
    private final Counter authLogout;
    private final Counter sessionCreated;
    private final Counter sessionExpired;
    private final Counter tokenRefresh;
    private final Counter tokenRefreshFailure;

    private final Counter abacDecisionAllowed;
    private final Counter abacDecisionDenied;
    private final Counter abacCacheHit;
    private final Counter abacCacheMiss;

    private final Counter documentUploadSuccess;
    private final Counter documentUploadFailure;
    private final Counter documentDownload;
    private final Counter documentDelete;
    private final DistributionSummary documentSize;

    private final Timer permissionsApiTimer;
    private final Counter permissionsApiSuccess;
    private final Counter permissionsApiFailure;

    public BusinessMetrics(@NonNull MeterRegistry registry) {
        this.registry = registry;

        this.authLoginSuccess = Counter.builder("auth.login")
                .tag("outcome", OUTCOME_SUCCESS)
                .description("Successful login attempts")
                .register(registry);

        this.authLoginFailure = Counter.builder("auth.login")
                .tag("outcome", OUTCOME_FAILURE)
                .description("Failed login attempts")
                .register(registry);

        this.authLogout = Counter.builder("auth.logout")
                .description("Logout events")
                .register(registry);

        this.sessionCreated = Counter.builder("session.lifecycle")
                .tag("event", "created")
                .description("Sessions created")
                .register(registry);

        this.sessionExpired = Counter.builder("session.lifecycle")
                .tag("event", "expired")
                .description("Sessions expired")
                .register(registry);

        this.tokenRefresh = Counter.builder("auth.token.refresh")
                .tag("outcome", OUTCOME_SUCCESS)
                .description("Successful token refresh attempts")
                .register(registry);

        this.tokenRefreshFailure = Counter.builder("auth.token.refresh")
                .tag("outcome", OUTCOME_FAILURE)
                .description("Failed token refresh attempts")
                .register(registry);

        this.abacDecisionAllowed = Counter.builder("abac.decision")
                .tag("result", "allowed")
                .description("ABAC decisions that allowed access")
                .register(registry);

        this.abacDecisionDenied = Counter.builder("abac.decision")
                .tag("result", "denied")
                .description("ABAC decisions that denied access")
                .register(registry);

        this.abacCacheHit = Counter.builder("abac.cache")
                .tag("result", "hit")
                .description("ABAC permission cache hits")
                .register(registry);

        this.abacCacheMiss = Counter.builder("abac.cache")
                .tag("result", "miss")
                .description("ABAC permission cache misses")
                .register(registry);

        this.documentUploadSuccess = Counter.builder("document.upload")
                .tag("outcome", OUTCOME_SUCCESS)
                .description("Successful document uploads")
                .register(registry);

        this.documentUploadFailure = Counter.builder("document.upload")
                .tag("outcome", OUTCOME_FAILURE)
                .description("Failed document uploads")
                .register(registry);

        this.documentDownload = Counter.builder("document.download")
                .description("Document downloads")
                .register(registry);

        this.documentDelete = Counter.builder("document.delete")
                .description("Document deletions")
                .register(registry);

        this.documentSize = DistributionSummary.builder("document.size.bytes")
                .description("Size of uploaded documents in bytes")
                .baseUnit("bytes")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99)
                .register(registry);

        this.permissionsApiTimer = Timer.builder("external.permissions.api")
                .description("Permissions API call duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        this.permissionsApiSuccess = Counter.builder("external.permissions.api.calls")
                .tag("outcome", OUTCOME_SUCCESS)
                .description("Successful permissions API calls")
                .register(registry);

        this.permissionsApiFailure = Counter.builder("external.permissions.api.calls")
                .tag("outcome", OUTCOME_FAILURE)
                .description("Failed permissions API calls")
                .register(registry);
    }

    public void recordLoginSuccess(@Nullable String persona) {
        authLoginSuccess.increment();
        registry.counter("auth.login.by_persona",
                Tags.of("persona", sanitizeTag(persona), "outcome", OUTCOME_SUCCESS))
                .increment();
    }

    public void recordLoginFailure(@Nullable String reason) {
        authLoginFailure.increment();
        registry.counter("auth.login.failure.by_reason",
                Tags.of("reason", sanitizeTag(reason)))
                .increment();
    }

    public void recordLogout() {
        authLogout.increment();
    }

    public void recordSessionCreated() {
        sessionCreated.increment();
    }

    public void recordSessionExpired() {
        sessionExpired.increment();
    }

    public void recordTokenRefresh(boolean success) {
        if (success) {
            tokenRefresh.increment();
        } else {
            tokenRefreshFailure.increment();
        }
    }

    public void recordAbacDecision(boolean allowed, @Nullable String policyId, @Nullable String action) {
        if (allowed) {
            abacDecisionAllowed.increment();
        } else {
            abacDecisionDenied.increment();
        }

        registry.counter("abac.decision.detailed",
                Tags.of("result", allowed ? "allowed" : "denied",
                        "policy", sanitizeTag(policyId),
                        "action", sanitizeTag(action)))
                .increment();
    }

    public void recordAbacCacheHit() {
        abacCacheHit.increment();
    }

    public void recordAbacCacheMiss() {
        abacCacheMiss.increment();
    }

    public void recordDocumentUpload(boolean success, @Nullable String documentType, long sizeBytes) {
        if (success) {
            documentUploadSuccess.increment();
            documentSize.record(sizeBytes);
            registry.counter("document.upload.by_type",
                    Tags.of("type", sanitizeTag(documentType), "outcome", OUTCOME_SUCCESS))
                    .increment();
        } else {
            documentUploadFailure.increment();
        }
    }

    public void recordDocumentDownload(@Nullable String documentType) {
        documentDownload.increment();
        registry.counter("document.download.by_type",
                Tags.of("type", sanitizeTag(documentType)))
                .increment();
    }

    public void recordDocumentDelete(@Nullable String documentType) {
        documentDelete.increment();
        registry.counter("document.delete.by_type",
                Tags.of("type", sanitizeTag(documentType)))
                .increment();
    }

    public void recordPermissionsApiCall(boolean success, @NonNull Duration duration) {
        permissionsApiTimer.record(duration.toNanos(), TimeUnit.NANOSECONDS);
        if (success) {
            permissionsApiSuccess.increment();
        } else {
            permissionsApiFailure.increment();
        }
    }

    @NonNull
    public Timer.Sample startPermissionsApiTimer() {
        return Timer.start(registry);
    }

    public void stopPermissionsApiTimer(@NonNull Timer.Sample sample, boolean success) {
        sample.stop(permissionsApiTimer);
        if (success) {
            permissionsApiSuccess.increment();
        } else {
            permissionsApiFailure.increment();
        }
    }

    public void recordS3Operation(@NonNull String operation, boolean success, @NonNull Duration duration) {
        Timer.builder("s3.operation")
                .tag("operation", sanitizeTag(operation))
                .tag("outcome", success ? OUTCOME_SUCCESS : OUTCOME_FAILURE)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    @NonNull
    private String sanitizeTag(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return TAG_UNKNOWN;
        }
        String sanitized = value.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        if (sanitized.length() > MAX_TAG_LENGTH) {
            sanitized = sanitized.substring(0, MAX_TAG_LENGTH);
        }
        return sanitized.isBlank() ? TAG_UNKNOWN : sanitized;
    }
}
