package com.example.bff.authz.filter;

import com.example.bff.auth.model.AuthPrincipal;
import com.example.bff.auth.model.DelegateType;
import com.example.bff.auth.model.Persona;
import com.example.bff.authz.annotation.RequirePersona;
import com.example.bff.authz.model.ManagedMemberAccess;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.common.filter.FilterResponseUtils;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.MfeProxyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * WebFilter that enforces {@link RequirePersona} annotations on controller methods.
 *
 * <p>This filter runs after {@link com.example.bff.auth.filter.DualAuthWebFilter}
 * (which creates AuthPrincipal) and validates that the authenticated user's
 * persona matches the requirements.</p>
 *
 * <h3>Authorization Flow:</h3>
 * <ul>
 *   <li><b>PROXY:</b> Skip permission validation (trust caller)</li>
 *   <li><b>INDIVIDUAL_SELF:</b> Use principal.enterpriseId() as target (own data)</li>
 *   <li><b>DELEGATE:</b> Read X-Enterprise-Id header, validate permissions for that dependent</li>
 * </ul>
 *
 * <p>Order: HIGHEST_PRECEDENCE + 30 (after DualAuthWebFilter at +20)</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.authz.persona-filter.enabled", havingValue = "true", matchIfMissing = true)
public class PersonaAuthorizationFilter implements WebFilter {

    private final RequestMappingHandlerMapping handlerMapping;
    private final MfeProxyProperties proxyProperties;

    @Override
    @NonNull
    public Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        return handlerMapping.getHandler(exchange)
                .flatMap(handler -> {
                    if (handler instanceof HandlerMethod handlerMethod) {
                        RequirePersona annotation = handlerMethod.getMethodAnnotation(RequirePersona.class);
                        if (annotation != null) {
                            return validatePersona(exchange, annotation, chain);
                        }
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Void> validatePersona(
            ServerWebExchange exchange,
            RequirePersona annotation,
            WebFilterChain chain) {

        AuthPrincipal principal = exchange.getAttribute(AuthPrincipal.EXCHANGE_ATTRIBUTE);

        // No principal means authentication failed or was skipped
        if (principal == null) {
            log.debug("No AuthPrincipal found for @RequirePersona endpoint");
            return unauthorizedResponse(exchange, "AUTHENTICATION_REQUIRED", "Authentication required");
        }

        // Check if persona is in the allowed list
        Set<Persona> allowedPersonas = Arrays.stream(annotation.value()).collect(Collectors.toSet());
        if (!allowedPersonas.contains(principal.persona())) {
            log.warn("Persona {} not allowed. Required: {}", principal.persona(), allowedPersonas);
            return forbiddenResponse(exchange, "PERSONA_NOT_ALLOWED",
                    String.format("Persona '%s' is not authorized for this resource", principal.persona()));
        }

        // PROXY: Skip permission validation (trust caller)
        if (principal.isProxyAuth()) {
            log.debug("Proxy auth - skipping permission validation for {}",
                    exchange.getRequest().getPath());
            return chain.filter(exchange);
        }

        // For DELEGATE persona, validate permissions for target dependent
        if (principal.persona() == Persona.DELEGATE && annotation.requiredDelegates().length > 0) {
            return validateDelegatePermissions(exchange, principal, annotation, chain);
        }

        // Authorization passed (INDIVIDUAL_SELF or DELEGATE with no required delegates)
        log.debug("Persona authorization passed for {} on {}",
                principal.persona(), exchange.getRequest().getPath());
        return chain.filter(exchange);
    }

    /**
     * Validate DELEGATE permissions for the target dependent.
     *
     * <p>Reads X-Enterprise-Id header to determine target dependent,
     * then validates permissions from PermissionSet with date checking.
     */
    private Mono<Void> validateDelegatePermissions(
            ServerWebExchange exchange,
            AuthPrincipal principal,
            RequirePersona annotation,
            WebFilterChain chain) {

        Set<DelegateType> requiredTypes = Arrays.stream(annotation.requiredDelegates())
                .collect(Collectors.toSet());

        // Get target enterprise ID from header (for DELEGATE accessing dependent data)
        String targetEnterpriseId = getTargetEnterpriseId(exchange, principal);

        if (targetEnterpriseId == null || targetEnterpriseId.isBlank()) {
            log.warn("Missing target enterprise ID for DELEGATE persona");
            return forbiddenResponse(exchange, "MISSING_ENTERPRISE_ID",
                    "X-Enterprise-Id header is required for delegate access");
        }

        // Get permissions from principal
        PermissionSet permissions = principal.permissions();
        if (permissions == null) {
            log.warn("No permissions found for DELEGATE persona");
            return forbiddenResponse(exchange, "NO_PERMISSIONS",
                    "No permissions available for delegate");
        }

        // Look up permissions for target managed member
        Optional<ManagedMemberAccess> accessOpt = permissions.getAccessFor(targetEnterpriseId);
        if (accessOpt.isEmpty()) {
            log.warn("No access found for managed member {} by user {}",
                    StringSanitizer.forLog(targetEnterpriseId),
                    StringSanitizer.forLog(principal.loggedInMemberIdValue()));
            return forbiddenResponse(exchange, "NO_MANAGED_MEMBER_ACCESS",
                    String.format("No access permissions for managed member %s", targetEnterpriseId));
        }

        ManagedMemberAccess access = accessOpt.get();

        // Validate all required delegate types with date checking
        if (!access.hasAllValidPermissions(requiredTypes)) {
            Set<DelegateType> missing = requiredTypes.stream()
                    .filter(type -> !access.hasValidPermission(type))
                    .collect(Collectors.toSet());

            // Determine specific error reason
            String reason = determinePermissionError(access, missing);

            log.warn("Permission validation failed for dependent {}: missing={}, reason={}",
                    StringSanitizer.forLog(targetEnterpriseId), missing, reason);
            return forbiddenResponse(exchange, "MISSING_DELEGATE_PERMISSIONS",
                    String.format("Missing or invalid delegate permissions: %s. %s", missing, reason));
        }

        // Store validated enterprise ID in exchange for controller use
        exchange.getAttributes().put("VALIDATED_ENTERPRISE_ID", targetEnterpriseId);

        log.debug("Delegate permission validation passed for {} accessing {}",
                StringSanitizer.forLog(principal.loggedInMemberIdValue()),
                StringSanitizer.forLog(targetEnterpriseId));
        return chain.filter(exchange);
    }

    /**
     * Get target enterprise ID from header or fall back to principal.
     */
    @Nullable
    private String getTargetEnterpriseId(ServerWebExchange exchange, AuthPrincipal principal) {
        // Try to get from header first
        String headerName = proxyProperties.headers().enterpriseId();
        String headerValue = exchange.getRequest().getHeaders().getFirst(headerName);

        if (headerValue != null && !headerValue.isBlank()) {
            return StringSanitizer.headerValue(headerValue);
        }

        // Fall back to principal's enterprise ID (for backward compatibility)
        return principal.enterpriseId();
    }

    /**
     * Determine specific error reason for permission failure.
     */
    private String determinePermissionError(ManagedMemberAccess access, Set<DelegateType> missingTypes) {
        for (DelegateType type : missingTypes) {
            var perm = access.getPermission(type);
            if (perm == null) {
                return String.format("Permission %s not granted", type);
            }
            if (!perm.active()) {
                return String.format("Permission %s is deactivated", type);
            }
            if (perm.isExpired()) {
                return String.format("Permission %s has expired", type);
            }
            if (perm.isNotYetActive()) {
                return String.format("Permission %s is not yet active", type);
            }
        }
        return "Permission validation failed";
    }

    @NonNull
    private Mono<Void> unauthorizedResponse(
            @NonNull ServerWebExchange exchange,
            @NonNull String code,
            @NonNull String message) {
        return FilterResponseUtils.unauthorized(exchange, code, message, null);
    }

    @NonNull
    private Mono<Void> forbiddenResponse(
            @NonNull ServerWebExchange exchange,
            @NonNull String code,
            @NonNull String message) {
        return FilterResponseUtils.forbidden(exchange, code, message, null);
    }
}
