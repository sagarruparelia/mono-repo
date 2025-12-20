package com.example.bff.auth.handler;

import com.example.bff.auth.model.TokenData;
import com.example.bff.auth.service.TokenService;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.authz.service.PermissionsFetchService;
import com.example.bff.identity.exception.AgeRestrictionException;
import com.example.bff.identity.exception.NoAccessException;
import com.example.bff.identity.model.MemberAccess;
import com.example.bff.identity.service.MemberAccessOrchestrator;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

/**
 * Handles successful HSID authentication.
 * Enriches the session with member access information from external APIs.
 */
@Component
public class HsidAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(HsidAuthenticationSuccessHandler.class);
    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";
    private static final String REDIRECT_URI_COOKIE = "redirect_uri";
    private static final String AGE_RESTRICTED_PATH = "/error/age-restricted";
    private static final String NO_ACCESS_PATH = "/error/no-access";
    private static final String ERROR_PATH = "/error";

    private final SessionService sessionService;
    private final MemberAccessOrchestrator memberAccessOrchestrator;

    @Nullable
    private final PermissionsFetchService permissionsFetchService;

    @Nullable
    private final TokenService tokenService;

    @Nullable
    private final ReactiveOAuth2AuthorizedClientService authorizedClientService;

    public HsidAuthenticationSuccessHandler(
            @NonNull SessionService sessionService,
            @NonNull MemberAccessOrchestrator memberAccessOrchestrator,
            @Nullable PermissionsFetchService permissionsFetchService,
            @Nullable TokenService tokenService,
            @Nullable ReactiveOAuth2AuthorizedClientService authorizedClientService) {
        this.sessionService = sessionService;
        this.memberAccessOrchestrator = memberAccessOrchestrator;
        this.permissionsFetchService = permissionsFetchService;
        this.tokenService = tokenService;
        this.authorizedClientService = authorizedClientService;
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange exchange,
                                               Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) {
            log.warn("Unexpected authentication type: {}", authentication.getClass());
            return Mono.empty();
        }

        if (!(token.getPrincipal() instanceof OidcUser oidcUser)) {
            log.warn("Principal is not OidcUser");
            return Mono.empty();
        }

        String userId = oidcUser.getSubject();
        log.info("Authentication success for user: {}", userId);

        ClientInfo clientInfo = extractClientInfo(exchange.getExchange());
        Mono<OAuth2AuthorizedClient> authorizedClientMono = getAuthorizedClient(token);

        return sessionService.invalidateExistingSessions(userId)
                .then(memberAccessOrchestrator.resolveMemberAccess(userId))
                .flatMap(memberAccess -> createSessionAndRedirect(
                        exchange, userId, oidcUser, clientInfo, memberAccess, authorizedClientMono))
                .onErrorResume(AgeRestrictionException.class, e -> {
                    log.warn("User {} failed age restriction check: {}", userId, e.getMessage());
                    return redirectToError(exchange.getExchange(), AGE_RESTRICTED_PATH);
                })
                .onErrorResume(NoAccessException.class, e -> {
                    log.warn("User {} has no access: {}", userId, e.getMessage());
                    return redirectToError(exchange.getExchange(), NO_ACCESS_PATH);
                })
                .onErrorResume(e -> {
                    log.error("Failed to enrich member access for user {}: {}", userId, e.getMessage(), e);
                    return redirectToError(exchange.getExchange(), ERROR_PATH);
                });
    }

    /**
     * Creates session with member access and redirects to app.
     */
    private Mono<Void> createSessionAndRedirect(
            WebFilterExchange exchange,
            String userId,
            OidcUser oidcUser,
            ClientInfo clientInfo,
            MemberAccess memberAccess,
            Mono<OAuth2AuthorizedClient> authorizedClientMono) {

        log.info("Member access resolved for user {}: persona={}, eligibility={}",
                userId, memberAccess.getEffectivePersona(), memberAccess.eligibilityStatus());

        Mono<PermissionSet> permissionsMono = fetchPermissions(userId, memberAccess);

        return Mono.zip(permissionsMono, authorizedClientMono.defaultIfEmpty(createEmptyClient()))
                .flatMap(tuple -> {
                    PermissionSet permissions = tuple.getT1();
                    OAuth2AuthorizedClient authorizedClient = tuple.getT2();

                    return sessionService.createSessionWithMemberAccess(
                            userId, oidcUser, clientInfo, memberAccess, permissions)
                            .flatMap(sessionId -> storeTokensAndRedirect(
                                    exchange.getExchange(), sessionId, authorizedClient, oidcUser));
                });
    }

    /**
     * Fetches permissions based on resolved member access.
     */
    private Mono<PermissionSet> fetchPermissions(String userId, MemberAccess memberAccess) {
        String persona = memberAccess.getEffectivePersona();

        if (permissionsFetchService == null) {
            log.debug("PermissionsFetchService not available, using empty permissions");
            return Mono.just(PermissionSet.empty(userId, persona));
        }

        return permissionsFetchService.fetchPermissions(userId)
                .doOnSuccess(p -> log.info("Fetched {} dependents for user {} on login",
                        p.dependents() != null ? p.dependents().size() : 0, userId))
                .onErrorResume(e -> {
                    log.error("Failed to fetch permissions for user {}: {}", userId, e.getMessage());
                    return Mono.just(PermissionSet.empty(userId, persona));
                });
    }

    private Mono<Void> redirectToError(ServerWebExchange exchange, String errorPath) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(errorPath));
        return exchange.getResponse().setComplete();
    }

    private Mono<OAuth2AuthorizedClient> getAuthorizedClient(OAuth2AuthenticationToken token) {
        if (authorizedClientService == null) {
            log.debug("AuthorizedClientService not available, skipping token storage");
            return Mono.empty();
        }

        return authorizedClientService.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(),
                token.getName()
        );
    }

    private OAuth2AuthorizedClient createEmptyClient() {
        return null;
    }

    private Mono<Void> storeTokensAndRedirect(
            ServerWebExchange exchange,
            String sessionId,
            @Nullable OAuth2AuthorizedClient authorizedClient,
            OidcUser oidcUser) {

        Mono<Void> storeTokensMono = Mono.empty();

        if (tokenService != null && authorizedClient != null) {
            TokenData tokenData = extractTokenData(authorizedClient, oidcUser);
            if (tokenData != null) {
                storeTokensMono = tokenService.storeTokens(sessionId, tokenData)
                        .doOnSuccess(v -> log.debug("Tokens stored for session {}", sessionId))
                        .onErrorResume(e -> {
                            log.warn("Failed to store tokens: {}", e.getMessage());
                            return Mono.empty();
                        });
            }
        }

        return storeTokensMono.then(setSessionCookieAndRedirect(exchange, sessionId));
    }

    @Nullable
    private TokenData extractTokenData(OAuth2AuthorizedClient authorizedClient, OidcUser oidcUser) {
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

        if (accessToken == null) {
            log.warn("No access token available");
            return null;
        }

        String idToken = oidcUser.getIdToken() != null ? oidcUser.getIdToken().getTokenValue() : null;
        Instant accessTokenExpiry = accessToken.getExpiresAt();
        Instant refreshTokenExpiry = refreshToken != null ? refreshToken.getExpiresAt() : null;

        log.debug("Extracted tokens - access expires: {}, refresh expires: {}",
                accessTokenExpiry, refreshTokenExpiry);

        return new TokenData(
                accessToken.getTokenValue(),
                refreshToken != null ? refreshToken.getTokenValue() : null,
                idToken,
                accessTokenExpiry,
                refreshTokenExpiry
        );
    }

    private ClientInfo extractClientInfo(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        String ipAddress = request.getHeaders().getFirst("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = request.getRemoteAddress() != null
                    ? request.getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
        } else {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        String userAgent = request.getHeaders().getFirst("User-Agent");

        return ClientInfo.of(ipAddress, userAgent != null ? userAgent : "unknown");
    }

    private Mono<Void> setSessionCookieAndRedirect(ServerWebExchange exchange, String sessionId) {
        ResponseCookie sessionCookie = ResponseCookie.from(SESSION_COOKIE_NAME, sessionId)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofMinutes(30))
                .sameSite("Lax")
                .build();

        exchange.getResponse().addCookie(sessionCookie);

        String redirectUri = getRedirectUri(exchange);

        ResponseCookie clearRedirectCookie = ResponseCookie.from(REDIRECT_URI_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .build();

        exchange.getResponse().addCookie(clearRedirectCookie);

        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(redirectUri));

        log.info("Session created, redirecting to: {}", redirectUri);

        return exchange.getResponse().setComplete();
    }

    private String getRedirectUri(ServerWebExchange exchange) {
        HttpCookie redirectCookie = exchange.getRequest().getCookies().getFirst(REDIRECT_URI_COOKIE);
        if (redirectCookie != null && !redirectCookie.getValue().isBlank()) {
            String uri = redirectCookie.getValue();
            if (isValidRelativePath(uri)) {
                return uri;
            }
            log.warn("Rejected potentially malicious redirect URI: {}", sanitizeForLog(uri));
        }
        return "/";
    }

    private boolean isValidRelativePath(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        if (!uri.startsWith("/") || uri.startsWith("//")) {
            return false;
        }
        String lowerUri = uri.toLowerCase();
        if (lowerUri.contains("://") || lowerUri.startsWith("javascript:") || lowerUri.startsWith("data:")) {
            return false;
        }
        if (uri.contains("%2f%2f") || uri.contains("%2F%2F")) {
            return false;
        }
        return true;
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        return value.replaceAll("[\\r\\n\\t]", "").substring(0, Math.min(value.length(), 64));
    }
}
