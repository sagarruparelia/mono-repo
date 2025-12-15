package com.example.bff.authz.audit;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.config.properties.AuthzProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.util.UUID;

/**
 * Service for publishing authorization audit events.
 * Events are logged in structured JSON format for security analysis.
 */
@Service
@ConditionalOnProperty(name = "app.authz.audit.enabled", havingValue = "true", matchIfMissing = true)
public class AuthzAuditService {

    private static final Logger auditLog = LoggerFactory.getLogger("AUTHZ_AUDIT");
    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private final ObjectMapper objectMapper;
    private final AuthzProperties authzProperties;

    public AuthzAuditService(ObjectMapper objectMapper, AuthzProperties authzProperties) {
        this.objectMapper = objectMapper;
        this.authzProperties = authzProperties;
    }

    /**
     * Log an authorization decision.
     */
    public void logDecision(
            SubjectAttributes subject,
            ResourceAttributes resource,
            Action action,
            PolicyDecision decision,
            ServerHttpRequest request) {

        AuthzAuditEvent.RequestContext context = extractRequestContext(request);
        AuthzAuditEvent event = AuthzAuditEvent.from(subject, resource, action, decision, context);

        logEvent(event);
    }

    /**
     * Log an authorization error.
     */
    public void logError(
            SubjectAttributes subject,
            ResourceAttributes resource,
            Action action,
            String errorReason,
            ServerHttpRequest request) {

        AuthzAuditEvent.RequestContext context = extractRequestContext(request);
        AuthzAuditEvent event = AuthzAuditEvent.error(subject, resource, action, errorReason, context);

        logEvent(event);
    }

    private void logEvent(AuthzAuditEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event.toStructuredLog());

            // Log at INFO for ALLOW, WARN for DENY, ERROR for errors
            switch (event.outcome()) {
                case ALLOW -> auditLog.info(json);
                case DENY -> auditLog.warn(json);
                case ERROR -> auditLog.error(json);
            }
        } catch (JsonProcessingException e) {
            auditLog.error("Failed to serialize audit event: {}", e.getMessage());
            // Fallback to simple logging
            auditLog.warn("AuthZ {} - user={}, resource={}/{}, action={}, policy={}, reason={}",
                    event.outcome(),
                    event.userId(),
                    event.resourceType(),
                    event.resourceId(),
                    event.action(),
                    event.policyId(),
                    event.reason());
        }
    }

    private AuthzAuditEvent.RequestContext extractRequestContext(ServerHttpRequest request) {
        if (request == null) {
            return AuthzAuditEvent.RequestContext.empty();
        }

        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        String clientIp = null;
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            clientIp = remoteAddress.getAddress().getHostAddress();
        }

        String forwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            clientIp = forwardedFor.split(",")[0].trim();
        }

        return new AuthzAuditEvent.RequestContext(
                correlationId,
                request.getPath().value(),
                request.getMethod().name(),
                clientIp,
                request.getHeaders().getFirst("User-Agent")
        );
    }
}
