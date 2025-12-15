package com.example.bff.authz.aspect;

import com.example.bff.authz.abac.model.Action;
import com.example.bff.authz.abac.model.ResourceAttributes;
import com.example.bff.authz.abac.model.SubjectAttributes;
import com.example.bff.authz.abac.service.AbacAuthorizationService;
import com.example.bff.authz.annotation.RequiresPermission;
import com.example.bff.authz.model.AuthType;
import com.example.bff.authz.model.Permission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpCookie;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Set;

/**
 * Aspect that enforces @RequiresPermission annotations using ABAC policy engine.
 */
@Aspect
@Component
@Order(1)
@ConditionalOnProperty(name = "app.authz.enabled", havingValue = "true")
public class AuthorizationAspect {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationAspect.class);
    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";

    @Nullable
    private final AbacAuthorizationService authorizationService;

    public AuthorizationAspect(@Nullable AbacAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Around("@annotation(requiresPermission)")
    public Object checkPermissions(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
        if (authorizationService == null) {
            log.warn("AbacAuthorizationService not available, skipping permission check");
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Extract dependentId from method arguments
        String resourceId = extractResourceId(joinPoint, method, requiresPermission.dependentIdParam());
        if (resourceId == null) {
            log.error("Could not extract resourceId from method arguments: {}", method.getName());
            throw new AccessDeniedException("Unable to determine resource ID");
        }

        // Extract ServerWebExchange from arguments
        ServerWebExchange exchange = extractExchange(joinPoint.getArgs());
        if (exchange == null) {
            log.error("No ServerWebExchange found in method arguments: {}", method.getName());
            throw new AccessDeniedException("Request context unavailable");
        }

        // Determine if sensitive access is required (ROI in permissions)
        Set<Permission> requiredPermissions = Set.of(requiresPermission.permissions());
        boolean isSensitive = requiredPermissions.contains(Permission.ROI);

        // Build resource attributes
        ResourceAttributes resource = ResourceAttributes.dependent(
                resourceId,
                isSensitive ? ResourceAttributes.Sensitivity.SENSITIVE : ResourceAttributes.Sensitivity.NORMAL
        );
        Action action = isSensitive ? Action.VIEW_SENSITIVE : Action.VIEW;

        // Determine auth type and build subject
        AuthType authType = authorizationService.determineAuthType(exchange.getRequest());

        Mono<SubjectAttributes> subjectMono = authType == AuthType.PROXY
                ? authorizationService.buildProxySubject(exchange.getRequest())
                : buildHsidSubject(exchange);

        // Perform authorization check
        return subjectMono
                .flatMap(subject -> authorizationService.authorize(subject, resource, action))
                .flatMap(decision -> {
                    if (decision.isAllowed()) {
                        log.debug("ABAC permission check passed for {} on resource {}",
                                method.getName(), resourceId);
                        try {
                            Object result = joinPoint.proceed();
                            if (result instanceof Mono) {
                                return (Mono<?>) result;
                            }
                            return Mono.justOrEmpty(result);
                        } catch (Throwable e) {
                            return Mono.error(e);
                        }
                    } else {
                        log.warn("ABAC permission denied for {} on resource {}: {}",
                                method.getName(), resourceId, decision.reason());
                        return Mono.error(new AccessDeniedException(decision.reason()));
                    }
                })
                .switchIfEmpty(Mono.error(new AccessDeniedException("Authentication required")));
    }

    private Mono<SubjectAttributes> buildHsidSubject(ServerWebExchange exchange) {
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);
        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            return Mono.empty();
        }
        return authorizationService.buildHsidSubject(sessionCookie.getValue());
    }

    private String extractResourceId(ProceedingJoinPoint joinPoint, Method method, String paramName) {
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            PathVariable pathVariable = parameters[i].getAnnotation(PathVariable.class);
            if (pathVariable != null) {
                String name = pathVariable.value().isEmpty()
                        ? parameters[i].getName()
                        : pathVariable.value();
                if (paramName.equals(name) && args[i] != null) {
                    return args[i].toString();
                }
            }
        }
        return null;
    }

    private ServerWebExchange extractExchange(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof ServerWebExchange) {
                return (ServerWebExchange) arg;
            }
        }
        return null;
    }
}
