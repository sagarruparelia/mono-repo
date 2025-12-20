package com.example.bff.authz.abac.service;

import com.example.bff.authz.abac.engine.AbacPolicyEngine;
import com.example.bff.authz.abac.model.*;
import com.example.bff.authz.audit.AuthzAuditService;
import com.example.bff.authz.model.AuthType;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.authz.service.PermissionsFetchService;
import com.example.bff.config.properties.MfeProxyProperties;
import com.example.bff.config.properties.PersonaProperties;
import com.example.bff.session.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Unified ABAC Authorization Service.
 * Handles both HSID (session-based) and Proxy (header-based) authorization.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.authz.enabled", havingValue = "true")
public class AbacAuthorizationService {

    private final AbacPolicyEngine policyEngine;

    @Nullable
    private final SessionService sessionService;

    @Nullable
    private final PermissionsFetchService permissionsFetchService;

    @Nullable
    private final AuthzAuditService auditService;

    private final MfeProxyProperties proxyProperties;
    private final PersonaProperties personaProperties;

    public AbacAuthorizationService(
            AbacPolicyEngine policyEngine,
            @Nullable SessionService sessionService,
            @Nullable PermissionsFetchService permissionsFetchService,
            @Nullable AuthzAuditService auditService,
            MfeProxyProperties proxyProperties,
            PersonaProperties personaProperties) {
        this.policyEngine = policyEngine;
        this.sessionService = sessionService;
        this.permissionsFetchService = permissionsFetchService;
        this.auditService = auditService;
        this.proxyProperties = proxyProperties;
        this.personaProperties = personaProperties;
    }

    /**
     * Check if access is allowed for the given request context.
     *
     * @param subject  Subject attributes
     * @param resource Resource attributes
     * @param action   Action being performed
     * @return Mono emitting the policy decision
     */
    public Mono<PolicyDecision> authorize(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        PolicyDecision decision = policyEngine.evaluate(subject, resource, action);
        return Mono.just(decision);
    }

    /**
     * Check if access is allowed with audit logging.
     *
     * @param subject  Subject attributes
     * @param resource Resource attributes
     * @param action   Action being performed
     * @param request  Server HTTP request for audit context
     * @return Mono emitting the policy decision
     */
    public Mono<PolicyDecision> authorize(
            SubjectAttributes subject,
            ResourceAttributes resource,
            Action action,
            ServerHttpRequest request) {

        PolicyDecision decision = policyEngine.evaluate(subject, resource, action);

        // Log audit event
        if (auditService != null) {
            auditService.logDecision(subject, resource, action, decision, request);
        }

        return Mono.just(decision);
    }

    /**
     * Check if access is allowed (convenience method).
     */
    public Mono<Boolean> isAllowed(SubjectAttributes subject, ResourceAttributes resource, Action action) {
        return authorize(subject, resource, action).map(PolicyDecision::isAllowed);
    }

    /**
     * Build subject attributes for HSID user from session.
     *
     * @param sessionId The session ID
     * @return Mono emitting subject attributes
     */
    public Mono<SubjectAttributes> buildHsidSubject(String sessionId) {
        if (sessionService == null) {
            log.warn("SessionService not available");
            return Mono.empty();
        }

        return sessionService.getSession(sessionId)
                .flatMap(session -> {
                    // Get permissions from session
                    return sessionService.getPermissions(sessionId)
                            .map(permissions -> SubjectAttributes.forHsid(
                                    session.userId(),
                                    session.persona(),
                                    permissions))
                            .switchIfEmpty(Mono.just(SubjectAttributes.forHsid(
                                    session.userId(),
                                    session.persona(),
                                    PermissionSet.empty(session.userId(), session.persona()))));
                });
    }

    /**
     * Build subject attributes for Proxy user from request headers.
     *
     * @param request The server HTTP request
     * @return Mono emitting subject attributes (empty if invalid)
     */
    public Mono<SubjectAttributes> buildProxySubject(ServerHttpRequest request) {
        MfeProxyProperties.ProxyHeaders headers = proxyProperties.headers();

        String persona = request.getHeaders().getFirst(headers.persona());
        String partnerId = request.getHeaders().getFirst(headers.partnerId());
        String memberId = request.getHeaders().getFirst(headers.memberId());
        String operatorId = request.getHeaders().getFirst(headers.operatorId());
        String operatorName = request.getHeaders().getFirst(headers.operatorName());

        if (persona == null || persona.isBlank()) {
            log.warn("Missing persona header for proxy request");
            return Mono.empty();
        }

        // Validate persona is allowed for proxy
        if (!personaProperties.proxy().allowed().contains(persona)) {
            log.warn("Invalid proxy persona: {}. Allowed: {}",
                    persona, personaProperties.proxy().allowed());
            return Mono.empty();
        }

        // Use operatorId as userId for proxy users
        String userId = operatorId != null ? operatorId : "proxy-" + persona;

        SubjectAttributes subject = SubjectAttributes.forProxy(
                userId, persona, partnerId, memberId, operatorId, operatorName);

        log.debug("Built proxy subject: userId={}, persona={}, memberId={}",
                userId, persona, memberId);

        return Mono.just(subject);
    }

    /**
     * Determine auth type from request.
     *
     * @param request The server HTTP request
     * @return AuthType (HSID or PROXY)
     */
    public AuthType determineAuthType(ServerHttpRequest request) {
        String authTypeHeader = request.getHeaders()
                .getFirst(proxyProperties.headers().authType());

        if ("proxy".equalsIgnoreCase(authTypeHeader)) {
            return AuthType.PROXY;
        }

        String persona = request.getHeaders()
                .getFirst(proxyProperties.headers().persona());

        if (persona != null && personaProperties.proxy().allowed().contains(persona)) {
            return AuthType.PROXY;
        }

        return AuthType.HSID;
    }

    /**
     * Refresh permissions for an HSID user.
     *
     * @param userId The user ID
     * @return Mono emitting the refreshed permission set
     */
    public Mono<PermissionSet> refreshPermissions(String userId) {
        if (permissionsFetchService == null || sessionService == null) {
            log.warn("PermissionsFetchService or SessionService not available");
            return Mono.empty();
        }

        return permissionsFetchService.fetchPermissions(userId)
                .flatMap(permissions ->
                        sessionService.getSessionIdForUser(userId)
                                .flatMap(sessionId -> sessionService.updatePermissions(sessionId, permissions))
                                .thenReturn(permissions))
                .doOnSuccess(p -> log.info("Refreshed permissions for user {}", userId))
                .doOnError(e -> log.error("Failed to refresh permissions for user {}: {}", userId, e.getMessage()));
    }
}
