package com.example.bff.authz.filter;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.abac.service.AbacAuthorizationService;
import com.example.bff.authz.model.AuthType;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.AuthzProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ABAC Authorization Filter - enforces attribute-based access control on protected paths.
 *
 * <p>Intercepts requests to /api/dependent/{id} and /api/member/{id} paths
 * and evaluates ABAC policies to determine access.
 *
 * @see AbacAuthorizationService
 * @see AuthzProperties
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.authz.enabled", havingValue = "true")
public class AuthorizationFilter implements WebFilter, Ordered {

    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";
    private static final int MAX_RESOURCE_ID_LENGTH = 128;

    @Nullable
    private final AbacAuthorizationService authorizationService;

    private final AuthzProperties authzProperties;

    // Compiled patterns from configuration
    private final Pattern resourcePathPattern;
    private final Pattern sensitivePathPattern;

    public AuthorizationFilter(
            @Nullable AbacAuthorizationService authorizationService,
            @NonNull AuthzProperties authzProperties) {
        this.authorizationService = authorizationService;
        this.authzProperties = authzProperties;

        // Compile resource path pattern from config
        this.resourcePathPattern = Pattern.compile(authzProperties.pathPatterns().resourcePattern());

        // Build sensitive path pattern from configured segments
        String sensitiveSegments = String.join("|", authzProperties.pathPatterns().sensitiveSegments());
        String sensitivePatternStr = String.format(
                "^/api/(?:dependent|member)/[^/]+/(?:%s)(?:/.*)?$", sensitiveSegments);
        this.sensitivePathPattern = Pattern.compile(sensitivePatternStr);

        log.info("AuthorizationFilter initialized with resource pattern: {} and sensitive segments: {}",
                authzProperties.pathPatterns().resourcePattern(),
                sensitiveSegments);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        if (!authzProperties.enabled() || authorizationService == null) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();

        // Check if this is a protected resource path
        Matcher resourceMatcher = resourcePathPattern.matcher(path);
        if (!resourceMatcher.matches()) {
            return chain.filter(exchange);
        }

        String resourceType = resourceMatcher.group(1); // "dependent" or "member"
        String resourceId = sanitizeResourceId(resourceMatcher.group(2));

        if (resourceId == null) {
            log.warn("Invalid resource ID format in request path");
            return forbiddenResponse(exchange, PolicyDecision.deny("INVALID_RESOURCE_ID", "Invalid resource identifier"));
        }

        boolean isSensitive = sensitivePathPattern.matcher(path).matches();

        // Build resource attributes
        ResourceAttributes resource = buildResourceAttributes(resourceType, resourceId, isSensitive);
        Action action = isSensitive ? Action.VIEW_SENSITIVE : Action.VIEW;

        // Determine auth type and build subject
        AuthType authType = authorizationService.determineAuthType(exchange.getRequest());

        return buildSubjectAttributes(exchange, authType)
                .flatMap(subject -> authorizationService.authorize(subject, resource, action, exchange.getRequest()))
                .flatMap(decision -> handleDecision(exchange, chain, decision, resourceId))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Could not build subject attributes for authorization");
                    return unauthorizedResponse(exchange, "Authentication required");
                }));
    }

    @NonNull
    private ResourceAttributes buildResourceAttributes(
            @NonNull String type,
            @NonNull String id,
            boolean isSensitive) {

        ResourceAttributes.Sensitivity sensitivity = isSensitive
                ? ResourceAttributes.Sensitivity.SENSITIVE
                : ResourceAttributes.Sensitivity.NORMAL;

        return "member".equals(type)
                ? ResourceAttributes.member(id, sensitivity)
                : ResourceAttributes.dependent(id, sensitivity);
    }

    @NonNull
    private Mono<SubjectAttributes> buildSubjectAttributes(
            @NonNull ServerWebExchange exchange,
            @NonNull AuthType authType) {

        if (authType == AuthType.PROXY) {
            return authorizationService.buildProxySubject(exchange.getRequest());
        }

        // HSID - get session from cookie
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);
        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            log.debug("No session cookie for HSID authorization");
            return Mono.empty();
        }

        String sessionId = sessionCookie.getValue();
        // Validate session ID format (UUID)
        if (!StringSanitizer.isValidSessionId(sessionId)) {
            log.warn("Invalid session ID format");
            return Mono.empty();
        }

        return authorizationService.buildHsidSubject(sessionId);
    }

    @NonNull
    private Mono<Void> handleDecision(
            @NonNull ServerWebExchange exchange,
            @NonNull WebFilterChain chain,
            @NonNull PolicyDecision decision,
            @NonNull String resourceId) {

        if (decision.isAllowed()) {
            log.debug("ABAC: Access ALLOWED - policy={}, resource={}",
                    StringSanitizer.forLog(decision.policyId()), StringSanitizer.forLog(resourceId));
            return chain.filter(exchange);
        }

        log.warn("ABAC: Access DENIED - policy={}, reason={}, resource={}",
                StringSanitizer.forLog(decision.policyId()),
                StringSanitizer.forLog(decision.reason()),
                StringSanitizer.forLog(resourceId));
        return forbiddenResponse(exchange, decision);
    }

    /**
     * Sanitizes and validates resource ID from URL path.
     *
     * @param resourceId the raw resource ID
     * @return sanitized resource ID or null if invalid
     */
    @Nullable
    private String sanitizeResourceId(@Nullable String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return null;
        }

        String trimmed = resourceId.trim();
        if (trimmed.length() > MAX_RESOURCE_ID_LENGTH) {
            return null;
        }

        // Allow alphanumeric, dash, underscore
        if (!trimmed.matches("^[a-zA-Z0-9_-]+$")) {
            return null;
        }

        return trimmed;
    }

    @NonNull
    private Mono<Void> unauthorizedResponse(
            @NonNull ServerWebExchange exchange,
            @NonNull String message) {

        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format(
                "{\"error\":\"unauthorized\",\"code\":\"AUTH_REQUIRED\",\"message\":\"%s\"}",
                StringSanitizer.escapeJson(message));

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    @NonNull
    private Mono<Void> forbiddenResponse(
            @NonNull ServerWebExchange exchange,
            @NonNull PolicyDecision decision) {

        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String missingAttrs = "";
        if (decision.missingAttributes() != null && !decision.missingAttributes().isEmpty()) {
            missingAttrs = ",\"missing\":[" +
                    decision.missingAttributes().stream()
                            .map(s -> "\"" + StringSanitizer.escapeJson(s) + "\"")
                            .collect(Collectors.joining(",")) +
                    "]";
        }

        String body = String.format(
                "{\"error\":\"access_denied\",\"code\":\"%s\",\"policy\":\"%s\",\"message\":\"%s\"%s}",
                StringSanitizer.escapeJson(decision.policyId() != null ? decision.policyId() : "UNKNOWN"),
                StringSanitizer.escapeJson(decision.policyId() != null ? decision.policyId() : "UNKNOWN"),
                StringSanitizer.escapeJson(decision.reason() != null ? decision.reason() : "Access denied"),
                missingAttrs);

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
