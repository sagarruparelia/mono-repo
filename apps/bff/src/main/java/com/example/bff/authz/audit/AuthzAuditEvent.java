package com.example.bff.authz.audit;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.model.AuthType;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Structured audit event for authorization decisions.
 * Designed for security logging, compliance, and debugging.
 */
public record AuthzAuditEvent(
        // Event metadata
        String eventId,
        Instant timestamp,
        String correlationId,

        // Decision
        Outcome outcome,
        String policyId,
        String reason,
        Set<String> missingAttributes,

        // Subject
        AuthType authType,
        String userId,
        String persona,
        String partnerId,
        String operatorId,

        // Resource
        String resourceType,
        String resourceId,
        String resourceSensitivity,

        // Action
        Action action,

        // Request context
        String path,
        String method,
        String clientIp,
        String userAgent
) {
    public enum Outcome {
        ALLOW, DENY, ERROR
    }

    /**
     * Creates an audit event from authorization context.
     */
    public static AuthzAuditEvent from(
            SubjectAttributes subject,
            ResourceAttributes resource,
            Action action,
            PolicyDecision decision,
            RequestContext requestContext) {

        return new AuthzAuditEvent(
                requestContext.correlationId(),
                Instant.now(),
                requestContext.correlationId(),
                decision.isAllowed() ? Outcome.ALLOW : Outcome.DENY,
                decision.policyId(),
                decision.reason(),
                decision.missingAttributes(),
                subject.authType(),
                subject.userId(),
                subject.persona(),
                subject.partnerId(),
                subject.loggedInMemberIdValue(),
                resource.type().name(),
                resource.id(),
                resource.sensitivity().name(),
                action,
                requestContext.path(),
                requestContext.method(),
                requestContext.clientIp(),
                requestContext.userAgent()
        );
    }

    /**
     * Creates an error audit event.
     */
    public static AuthzAuditEvent error(
            SubjectAttributes subject,
            ResourceAttributes resource,
            Action action,
            String errorReason,
            RequestContext requestContext) {

        return new AuthzAuditEvent(
                requestContext.correlationId(),
                Instant.now(),
                requestContext.correlationId(),
                Outcome.ERROR,
                "ERROR",
                errorReason,
                Set.of(),
                subject != null ? subject.authType() : null,
                subject != null ? subject.userId() : null,
                subject != null ? subject.persona() : null,
                subject != null ? subject.partnerId() : null,
                subject != null ? subject.loggedInMemberIdValue() : null,
                resource != null ? resource.type().name() : null,
                resource != null ? resource.id() : null,
                resource != null ? resource.sensitivity().name() : null,
                action,
                requestContext.path(),
                requestContext.method(),
                requestContext.clientIp(),
                requestContext.userAgent()
        );
    }

    /**
     * Converts event to structured map for JSON logging.
     */
    public Map<String, Object> toStructuredLog() {
        return Map.ofEntries(
                Map.entry("event_type", "authz_decision"),
                Map.entry("event_id", eventId),
                Map.entry("timestamp", timestamp.toString()),
                Map.entry("correlation_id", correlationId != null ? correlationId : ""),
                Map.entry("outcome", outcome.name()),
                Map.entry("policy_id", policyId != null ? policyId : ""),
                Map.entry("reason", reason != null ? reason : ""),
                Map.entry("missing_attributes", missingAttributes != null ? missingAttributes : Set.of()),
                Map.entry("auth_type", authType != null ? authType.name() : ""),
                Map.entry("user_id", userId != null ? userId : ""),
                Map.entry("persona", persona != null ? persona : ""),
                Map.entry("partner_id", partnerId != null ? partnerId : ""),
                Map.entry("operator_id", operatorId != null ? operatorId : ""),
                Map.entry("resource_type", resourceType != null ? resourceType : ""),
                Map.entry("resource_id", resourceId != null ? resourceId : ""),
                Map.entry("resource_sensitivity", resourceSensitivity != null ? resourceSensitivity : ""),
                Map.entry("action", action != null ? action.name() : ""),
                Map.entry("path", path != null ? path : ""),
                Map.entry("method", method != null ? method : ""),
                Map.entry("client_ip", clientIp != null ? clientIp : ""),
                Map.entry("user_agent", userAgent != null ? userAgent : "")
        );
    }

    /**
     * Request context for audit events.
     */
    public record RequestContext(
            String correlationId,
            String path,
            String method,
            String clientIp,
            String userAgent
    ) {
        public static RequestContext empty() {
            return new RequestContext(null, null, null, null, null);
        }
    }
}
