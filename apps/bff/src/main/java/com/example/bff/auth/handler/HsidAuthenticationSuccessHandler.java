package com.example.bff.auth.handler;

import com.example.bff.auth.model.TokenData;
import com.example.bff.auth.service.TokenService;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.authz.service.PermissionsFetchService;
import com.example.bff.config.properties.PersonaProperties;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
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
import java.util.List;

@Component
public class HsidAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(HsidAuthenticationSuccessHandler.class);
    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";
    private static final String REDIRECT_URI_COOKIE = "redirect_uri";

    @Nullable
    private final SessionService sessionService;

    @Nullable
    private final PermissionsFetchService permissionsFetchService;

    @Nullable
    private final TokenService tokenService;

    @Nullable
    private final ReactiveOAuth2AuthorizedClientService authorizedClientService;

    private final PersonaProperties personaConfig;

    public HsidAuthenticationSuccessHandler(
            @Nullable SessionService sessionService,
            @Nullable PermissionsFetchService permissionsFetchService,
            @Nullable TokenService tokenService,
            @Nullable ReactiveOAuth2AuthorizedClientService authorizedClientService,
            PersonaProperties personaConfig) {
        this.sessionService = sessionService;
        this.permissionsFetchService = permissionsFetchService;
        this.tokenService = tokenService;
        this.authorizedClientService = authorizedClientService;
        this.personaConfig = personaConfig;
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

        // Extract persona from HSID claims
        String persona = extractPersona(oidcUser);
        List<String> dependents = extractDependents(oidcUser, persona);

        // Extract client info for session binding
        ClientInfo clientInfo = extractClientInfo(exchange.getExchange());

        // Invalidate existing sessions (single session enforcement) and create new
        if (sessionService == null) {
            // No session service - just redirect without setting session cookie
            log.warn("SessionService not available, skipping session creation");
            return redirectToApp(exchange.getExchange());
        }

        // Fetch permissions if service is available
        Mono<PermissionSet> permissionsMono = fetchPermissions(userId, persona);

        // Get authorized client for token storage
        Mono<OAuth2AuthorizedClient> authorizedClientMono = getAuthorizedClient(token);

        return sessionService.invalidateExistingSessions(userId)
                .then(Mono.zip(permissionsMono, authorizedClientMono.defaultIfEmpty(createEmptyClient())))
                .flatMap(tuple -> {
                    PermissionSet permissions = tuple.getT1();
                    OAuth2AuthorizedClient authorizedClient = tuple.getT2();

                    // Use permissions to get viewable dependents (overrides token dependents)
                    log.info("Creating session with {} viewable dependents for user {}",
                            permissions.getViewableDependents().size(), userId);

                    return sessionService.createSessionWithPermissions(
                            userId, oidcUser, persona, clientInfo, permissions)
                            .flatMap(sessionId -> storeTokensAndRedirect(
                                    exchange.getExchange(), sessionId, authorizedClient, oidcUser));
                });
    }

    /**
     * Gets the OAuth2AuthorizedClient containing tokens.
     */
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

    /**
     * Creates an empty placeholder client when authorized client is not available.
     */
    private OAuth2AuthorizedClient createEmptyClient() {
        return null;
    }

    /**
     * Stores tokens and redirects to the app.
     */
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

    /**
     * Extracts token data from the authorized client and OIDC user.
     */
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

    private Mono<PermissionSet> fetchPermissions(String userId, String persona) {
        if (permissionsFetchService == null) {
            log.debug("PermissionsFetchService not available, using empty permissions");
            return Mono.just(PermissionSet.empty(userId, persona));
        }

        return permissionsFetchService.fetchPermissions(userId)
                .doOnSuccess(p -> log.info("Fetched {} dependents for user {} on login",
                        p.dependents() != null ? p.dependents().size() : 0, userId))
                .onErrorResume(e -> {
                    log.error("Failed to fetch permissions on login for user {}: {}",
                            userId, e.getMessage());
                    // Return empty permissions on error (fail closed - no dependent access)
                    return Mono.just(PermissionSet.empty(userId, persona));
                });
    }

    private String extractPersona(OidcUser oidcUser) {
        String claimName = personaConfig.hsid().claimName();
        String persona = oidcUser.getClaimAsString(claimName);

        if (persona == null || persona.isBlank()) {
            log.debug("No persona claim found, defaulting to 'individual'");
            return "individual";
        }

        // Validate persona is allowed
        if (!personaConfig.hsid().allowed().contains(persona)) {
            log.warn("Invalid persona '{}', defaulting to 'individual'", persona);
            return "individual";
        }

        return persona;
    }

    private List<String> extractDependents(OidcUser oidcUser, String persona) {
        if (!"parent".equals(persona)) {
            return null;
        }

        String dependentsClaim = personaConfig.hsid().parent().dependentsClaim();
        List<String> dependents = oidcUser.getClaimAsStringList(dependentsClaim);

        if (dependents == null || dependents.isEmpty()) {
            log.debug("Parent persona but no dependents found");
            return List.of();
        }

        log.debug("Found {} dependents for parent user", dependents.size());
        return dependents;
    }

    private ClientInfo extractClientInfo(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();

        // Get IP address (considering X-Forwarded-For)
        String ipAddress = request.getHeaders().getFirst("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = request.getRemoteAddress() != null
                    ? request.getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
        } else {
            // Take first IP if multiple (client IP)
            ipAddress = ipAddress.split(",")[0].trim();
        }

        String userAgent = request.getHeaders().getFirst("User-Agent");

        return ClientInfo.of(ipAddress, userAgent != null ? userAgent : "unknown");
    }

    private Mono<Void> setSessionCookieAndRedirect(ServerWebExchange exchange, String sessionId) {
        // Set session cookie
        ResponseCookie sessionCookie = ResponseCookie.from(SESSION_COOKIE_NAME, sessionId)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofMinutes(30))
                .sameSite("Lax")
                .build();

        exchange.getResponse().addCookie(sessionCookie);

        // Get redirect URI from cookie or default to /
        String redirectUri = getRedirectUri(exchange);

        // Clear the redirect URI cookie
        ResponseCookie clearRedirectCookie = ResponseCookie.from(REDIRECT_URI_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .build();

        exchange.getResponse().addCookie(clearRedirectCookie);

        // Redirect to the original page
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(redirectUri));

        log.info("Session created, redirecting to: {}", redirectUri);

        return exchange.getResponse().setComplete();
    }

    private String getRedirectUri(ServerWebExchange exchange) {
        HttpCookie redirectCookie = exchange.getRequest().getCookies().getFirst(REDIRECT_URI_COOKIE);
        if (redirectCookie != null && !redirectCookie.getValue().isBlank()) {
            String uri = redirectCookie.getValue();
            // Security: Only allow relative paths to prevent open redirect attacks
            if (isValidRelativePath(uri)) {
                return uri;
            }
            log.warn("Rejected potentially malicious redirect URI: {}", sanitizeForLog(uri));
        }
        return "/";
    }

    /**
     * Validates that the URI is a safe relative path.
     * Prevents open redirect attacks by rejecting:
     * - Absolute URLs (http://, https://, //)
     * - Protocol-relative URLs (//evil.com)
     * - Data URIs (data:)
     * - JavaScript URIs (javascript:)
     */
    private boolean isValidRelativePath(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }
        // Must start with / but not //
        if (!uri.startsWith("/") || uri.startsWith("//")) {
            return false;
        }
        // Block protocol handlers
        String lowerUri = uri.toLowerCase();
        if (lowerUri.contains("://") || lowerUri.startsWith("javascript:") || lowerUri.startsWith("data:")) {
            return false;
        }
        // Block encoded variants
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

    private Mono<Void> redirectToApp(ServerWebExchange exchange) {
        String redirectUri = getRedirectUri(exchange);
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(redirectUri));
        return exchange.getResponse().setComplete();
    }
}
