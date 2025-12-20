package com.example.bff.authz.audit;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.common.util.StringSanitizer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for publishing authorization audit events in structured JSON format.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.authz.audit.enabled", havingValue = "true", matchIfMissing = true)
public class AuthzAuditService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUTHZ_AUDIT");

    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    private static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_USER_AGENT = "User-Agent";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
            "^([0-9]{1,3}\\.){3}[0-9]{1,3}$|^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$");

    private static final int MAX_CORRELATION_ID_LENGTH = 64;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final int MAX_PATH_LENGTH = 2000;

    private final ObjectMapper objectMapper;

    public void logDecision(
            @NonNull SubjectAttributes subject,
            @NonNull ResourceAttributes resource,
            @NonNull Action action,
            @NonNull PolicyDecision decision,
            @Nullable ServerHttpRequest request) {

        AuthzAuditEvent.RequestContext context = extractRequestContext(request);
        AuthzAuditEvent event = AuthzAuditEvent.from(subject, resource, action, decision, context);

        logEvent(event);
    }

    public void logError(
            @NonNull SubjectAttributes subject,
            @NonNull ResourceAttributes resource,
            @NonNull Action action,
            @NonNull String errorReason,
            @Nullable ServerHttpRequest request) {

        AuthzAuditEvent.RequestContext context = extractRequestContext(request);
        AuthzAuditEvent event = AuthzAuditEvent.error(subject, resource, action, errorReason, context);

        logEvent(event);
    }

    private void logEvent(@NonNull AuthzAuditEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event.toStructuredLog());
            logByOutcome(event.outcome(), json);
        } catch (JsonProcessingException e) {
            AUDIT_LOG.error("Failed to serialize audit event: {}", StringSanitizer.forLog(e.getMessage()));
            logFallback(event);
        }
    }

    private void logByOutcome(@Nullable AuthzAuditEvent.Outcome outcome, String json) {
        if (outcome == null) {
            AUDIT_LOG.warn(json);
            return;
        }

        switch (outcome) {
            case ALLOW -> AUDIT_LOG.info(json);
            case DENY -> AUDIT_LOG.warn(json);
            case ERROR -> AUDIT_LOG.error(json);
        }
    }

    private void logFallback(@NonNull AuthzAuditEvent event) {
        AUDIT_LOG.warn("AuthZ {} - user={}, resource={}/{}, action={}, policy={}, reason={}",
                event.outcome(),
                StringSanitizer.forLog(event.userId()),
                StringSanitizer.forLog(event.resourceType()),
                StringSanitizer.forLog(event.resourceId()),
                event.action(),
                StringSanitizer.forLog(event.policyId()),
                StringSanitizer.forLog(event.reason()));
    }

    @NonNull
    private AuthzAuditEvent.RequestContext extractRequestContext(@Nullable ServerHttpRequest request) {
        if (request == null) {
            return AuthzAuditEvent.RequestContext.empty();
        }

        String correlationId = extractCorrelationId(request);
        String clientIp = extractClientIp(request);
        String userAgent = extractUserAgent(request);
        String path = sanitizePath(request.getPath().value());
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";

        return new AuthzAuditEvent.RequestContext(
                correlationId,
                path,
                method,
                clientIp,
                userAgent
        );
    }

    /**
     * Extracts and validates correlation ID from request headers.
     * Generates a new UUID if the header is missing or invalid.
     */
    @NonNull
    private String extractCorrelationId(@NonNull ServerHttpRequest request) {
        String correlationId = request.getHeaders().getFirst(HEADER_CORRELATION_ID);

        if (correlationId == null || correlationId.isBlank()) {
            return generateCorrelationId();
        }

        String sanitized = correlationId.trim();
        if (sanitized.length() > MAX_CORRELATION_ID_LENGTH) {
            AUDIT_LOG.debug("Correlation ID exceeds max length, generating new one");
            return generateCorrelationId();
        }

        // Accept UUID format or alphanumeric with dashes/underscores
        if (!UUID_PATTERN.matcher(sanitized).matches() &&
            !sanitized.matches("^[a-zA-Z0-9_-]+$")) {
            AUDIT_LOG.debug("Invalid correlation ID format, generating new one");
            return generateCorrelationId();
        }

        return sanitized;
    }

    @NonNull
    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Extracts client IP address from request, handling proxy headers.
     * X-Forwarded-For header is validated to prevent IP spoofing attacks.
     */
    @Nullable
    private String extractClientIp(@NonNull ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst(HEADER_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String firstIp = forwardedFor.split(",")[0].trim();
            if (isValidIpAddress(firstIp)) {
                return firstIp;
            }
            AUDIT_LOG.debug("Invalid IP in X-Forwarded-For header, falling back to remote address");
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return null;
    }

    private boolean isValidIpAddress(@Nullable String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return IP_ADDRESS_PATTERN.matcher(ip).matches();
    }

    @Nullable
    private String extractUserAgent(@NonNull ServerHttpRequest request) {
        String userAgent = request.getHeaders().getFirst(HEADER_USER_AGENT);
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return sanitizeUserAgent(userAgent);
    }

    @NonNull
    private String sanitizeUserAgent(@NonNull String userAgent) {
        String sanitized = userAgent
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", " ");

        if (sanitized.length() > MAX_USER_AGENT_LENGTH) {
            sanitized = sanitized.substring(0, MAX_USER_AGENT_LENGTH) + "...";
        }
        return sanitized;
    }

    @NonNull
    private String sanitizePath(@Nullable String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }

        String sanitized = path
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "");

        if (sanitized.length() > MAX_PATH_LENGTH) {
            sanitized = sanitized.substring(0, MAX_PATH_LENGTH) + "...";
        }
        return sanitized;
    }
}
