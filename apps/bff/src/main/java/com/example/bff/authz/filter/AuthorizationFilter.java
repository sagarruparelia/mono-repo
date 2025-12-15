package com.example.bff.authz.filter;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.PolicyDecision;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.abac.service.AbacAuthorizationService;
import com.example.bff.authz.model.AuthType;
import com.example.bff.config.properties.AuthzProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ABAC Authorization Filter - enforces attribute-based access control on protected paths.
 *
 * <p>Intercepts requests to /api/dependent/{id} and /api/member/{id} paths
 * and evaluates ABAC policies to determine access.
 */
@Component
@ConditionalOnProperty(name = "app.authz.enabled", havingValue = "true")
public class AuthorizationFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationFilter.class);
    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";

    // Pattern to extract resourceId from path: /api/dependent/{id}/... or /api/member/{id}/...
    private static final Pattern RESOURCE_PATH_PATTERN =
            Pattern.compile("^/api/(dependent|member)/([^/]+)(?:/.*)?$");

    // Pattern for sensitive data paths
    private static final Pattern SENSITIVE_PATH_PATTERN =
            Pattern.compile("^/api/(?:dependent|member)/[^/]+/(?:medical|sensitive|records)(?:/.*)?$");

    @Nullable
    private final AbacAuthorizationService authorizationService;

    private final AuthzProperties authzProperties;

    public AuthorizationFilter(
            @Nullable AbacAuthorizationService authorizationService,
            AuthzProperties authzProperties) {
        this.authorizationService = authorizationService;
        this.authzProperties = authzProperties;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!authzProperties.enabled() || authorizationService == null) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();

        // Check if this is a protected resource path
        Matcher resourceMatcher = RESOURCE_PATH_PATTERN.matcher(path);
        if (!resourceMatcher.matches()) {
            return chain.filter(exchange);
        }

        String resourceType = resourceMatcher.group(1); // "dependent" or "member"
        String resourceId = resourceMatcher.group(2);
        boolean isSensitive = SENSITIVE_PATH_PATTERN.matcher(path).matches();

        // Build resource attributes
        ResourceAttributes resource = buildResourceAttributes(resourceType, resourceId, isSensitive);
        Action action = isSensitive ? Action.VIEW_SENSITIVE : Action.VIEW;

        // Determine auth type and build subject
        AuthType authType = authorizationService.determineAuthType(exchange.getRequest());

        return buildSubjectAttributes(exchange, authType)
                .flatMap(subject -> authorizationService.authorize(subject, resource, action))
                .flatMap(decision -> handleDecision(exchange, chain, decision, resourceId))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Could not build subject attributes for authorization");
                    return unauthorizedResponse(exchange, "Authentication required");
                }));
    }

    private ResourceAttributes buildResourceAttributes(String type, String id, boolean isSensitive) {
        ResourceAttributes.Sensitivity sensitivity = isSensitive
                ? ResourceAttributes.Sensitivity.SENSITIVE
                : ResourceAttributes.Sensitivity.NORMAL;

        return "member".equals(type)
                ? ResourceAttributes.member(id, sensitivity)
                : ResourceAttributes.dependent(id, sensitivity);
    }

    private Mono<SubjectAttributes> buildSubjectAttributes(ServerWebExchange exchange, AuthType authType) {
        if (authType == AuthType.PROXY) {
            return authorizationService.buildProxySubject(exchange.getRequest());
        }

        // HSID - get session from cookie
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);
        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            log.debug("No session cookie for HSID authorization");
            return Mono.empty();
        }

        return authorizationService.buildHsidSubject(sessionCookie.getValue());
    }

    private Mono<Void> handleDecision(
            ServerWebExchange exchange,
            WebFilterChain chain,
            PolicyDecision decision,
            String resourceId) {

        if (decision.isAllowed()) {
            log.debug("ABAC: Access ALLOWED - policy={}, resource={}",
                    decision.policyId(), resourceId);
            return chain.filter(exchange);
        }

        log.warn("ABAC: Access DENIED - policy={}, reason={}, resource={}",
                decision.policyId(), decision.reason(), resourceId);
        return forbiddenResponse(exchange, decision);
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("""
                {"error":"unauthorized","code":"AUTH_REQUIRED","message":"%s"}""", message);

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body.getBytes())));
    }

    private Mono<Void> forbiddenResponse(ServerWebExchange exchange, PolicyDecision decision) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String missingAttrs = decision.missingAttributes().isEmpty()
                ? ""
                : ",\"missing\":[" + decision.missingAttributes().stream()
                        .map(s -> "\"" + s + "\"")
                        .reduce((a, b) -> a + "," + b)
                        .orElse("") + "]";

        String body = String.format("""
                {"error":"access_denied","code":"%s","policy":"%s","message":"%s"%s}""",
                decision.policyId(),
                decision.policyId(),
                decision.reason().replace("\"", "'"),
                missingAttrs);

        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse()
                        .bufferFactory()
                        .wrap(body.getBytes())));
    }
}
