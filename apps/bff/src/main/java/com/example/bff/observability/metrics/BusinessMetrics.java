package com.example.bff.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Centralized service for recording business-specific metrics. */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    private final Counter authLoginSuccess;
    private final Counter authLoginFailure;
    private final Counter authLogout;
    private final Counter sessionCreated;
    private final Counter sessionExpired;
    private final Counter tokenRefresh;

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

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.authLoginSuccess = Counter.builder("auth.login")
                .tag("outcome", "success")
                .description("Successful login attempts")
                .register(registry);

        this.authLoginFailure = Counter.builder("auth.login")
                .tag("outcome", "failure")
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
                .description("Token refresh attempts")
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
                .tag("outcome", "success")
                .description("Successful document uploads")
                .register(registry);

        this.documentUploadFailure = Counter.builder("document.upload")
                .tag("outcome", "failure")
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
                .tag("outcome", "success")
                .description("Successful permissions API calls")
                .register(registry);

        this.permissionsApiFailure = Counter.builder("external.permissions.api.calls")
                .tag("outcome", "failure")
                .description("Failed permissions API calls")
                .register(registry);
    }

    public void recordLoginSuccess(String persona) {
        authLoginSuccess.increment();
        Counter.builder("auth.login.by_persona")
                .tag("persona", persona)
                .tag("outcome", "success")
                .register(registry)
                .increment();
    }

    public void recordLoginFailure(String reason) {
        authLoginFailure.increment();
        Counter.builder("auth.login.failure.by_reason")
                .tag("reason", sanitizeTag(reason))
                .register(registry)
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
        tokenRefresh.increment();
        if (!success) {
            Counter.builder("auth.token.refresh.failure")
                    .register(registry)
                    .increment();
        }
    }

    public void recordAbacDecision(boolean allowed, String policyId, String action) {
        if (allowed) {
            abacDecisionAllowed.increment();
        } else {
            abacDecisionDenied.increment();
        }

        Counter.builder("abac.decision.detailed")
                .tag("result", allowed ? "allowed" : "denied")
                .tag("policy", sanitizeTag(policyId))
                .tag("action", sanitizeTag(action))
                .register(registry)
                .increment();
    }

    public void recordAbacCacheHit() {
        abacCacheHit.increment();
    }

    public void recordAbacCacheMiss() {
        abacCacheMiss.increment();
    }

    public void recordDocumentUpload(boolean success, String documentType, long sizeBytes) {
        if (success) {
            documentUploadSuccess.increment();
            documentSize.record(sizeBytes);

            Counter.builder("document.upload.by_type")
                    .tag("type", sanitizeTag(documentType))
                    .tag("outcome", "success")
                    .register(registry)
                    .increment();
        } else {
            documentUploadFailure.increment();
        }
    }

    public void recordDocumentDownload(String documentType) {
        documentDownload.increment();
        Counter.builder("document.download.by_type")
                .tag("type", sanitizeTag(documentType))
                .register(registry)
                .increment();
    }

    public void recordDocumentDelete(String documentType) {
        documentDelete.increment();
        Counter.builder("document.delete.by_type")
                .tag("type", sanitizeTag(documentType))
                .register(registry)
                .increment();
    }

    public void recordPermissionsApiCall(boolean success, Duration duration) {
        permissionsApiTimer.record(duration.toNanos(), TimeUnit.NANOSECONDS);
        if (success) {
            permissionsApiSuccess.increment();
        } else {
            permissionsApiFailure.increment();
        }
    }

    public Timer.Sample startPermissionsApiTimer() {
        return Timer.start(registry);
    }

    public void stopPermissionsApiTimer(Timer.Sample sample, boolean success) {
        sample.stop(permissionsApiTimer);
        if (success) {
            permissionsApiSuccess.increment();
        } else {
            permissionsApiFailure.increment();
        }
    }

    public void recordS3Operation(String operation, boolean success, Duration duration) {
        Timer.builder("s3.operation")
                .tag("operation", operation)
                .tag("outcome", success ? "success" : "failure")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    private String sanitizeTag(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String sanitized = value.toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_")
                .substring(0, Math.min(value.length(), 50));
        return sanitized.isBlank() ? "unknown" : sanitized;
    }
}
