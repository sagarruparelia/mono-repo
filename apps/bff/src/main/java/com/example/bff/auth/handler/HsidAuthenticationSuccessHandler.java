package com.example.bff.auth.handler;

import com.example.bff.auth.model.TokenData;
import com.example.bff.auth.service.TokenService;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.authz.service.PermissionsFetchService;
import com.example.bff.health.service.HealthDataOrchestrator;
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

    @Nullable
    private final HealthDataOrchestrator healthDataOrchestrator;

    public HsidAuthenticationSuccessHandler(
            @NonNull SessionService sessionService,
            @NonNull MemberAccessOrchestrator memberAccessOrchestrator,
            @Nullable PermissionsFetchService permissionsFetchService,
            @Nullable TokenService tokenService,
            @Nullable ReactiveOAuth2AuthorizedClientService authorizedClientService,
            @Nullable HealthDataOrchestrator healthDataOrchestrator) {
        this.sessionService = sessionService;
        this.memberAccessOrchestrator = memberAccessOrchestrator;
        this.permissionsFetchService = permissionsFetchService;
        this.tokenService = tokenService;
        this.authorizedClientService = authorizedClientService;
        this.healthDataOrchestrator = healthDataOrchestrator;
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
                            .flatMap(sessionId -> {
                                // Trigger background health data fetch (fire-and-forget)
                                triggerHealthDataFetch(memberAccess);

                                return storeTokensAndRedirect(
                                        exchange.getExchange(), sessionId, authorizedClient, oidcUser);
                            });
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

    /**
     * Validates that a URI is a safe relative path to prevent open redirect attacks.
     * Uses recursive URL decoding to catch multi-layer encoding attacks.
     */
    private boolean isValidRelativePath(String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }

        // Length limit to prevent DoS via extremely long URIs
        if (uri.length() > 2048) {
            return false;
        }

        // Recursively decode to catch multi-layer encoding attacks
        String decoded = fullyDecodeUri(uri);
        if (decoded == null) {
            return false; // Decoding failed (malformed encoding)
        }

        // Must start with single forward slash (not //)
        if (!decoded.startsWith("/") || decoded.startsWith("//")) {
            return false;
        }

        // Check both original and decoded versions
        String lowerDecoded = decoded.toLowerCase();
        String lowerUri = uri.toLowerCase();

        // Block protocol handlers (including encoded variants)
        if (containsProtocolHandler(lowerDecoded) || containsProtocolHandler(lowerUri)) {
            return false;
        }

        // Block backslash variations (can be interpreted as // on some systems)
        if (decoded.contains("\\") || uri.contains("\\")) {
            return false;
        }

        // Block control characters and null bytes
        if (containsControlCharacters(decoded) || containsControlCharacters(uri)) {
            return false;
        }

        // Block double slashes anywhere in the decoded path
        if (decoded.contains("//")) {
            return false;
        }

        // Block parent directory traversal
        if (decoded.contains("/../") || decoded.endsWith("/..")) {
            return false;
        }

        return true;
    }

    /**
     * Recursively decodes a URI until no more decoding is needed.
     * Returns null if decoding fails (malformed input).
     */
    private String fullyDecodeUri(String uri) {
        if (uri == null) {
            return null;
        }

        String current = uri;
        int maxIterations = 10; // Prevent infinite loops on malicious input

        for (int i = 0; i < maxIterations; i++) {
            try {
                String decoded = java.net.URLDecoder.decode(current, java.nio.charset.StandardCharsets.UTF_8);
                if (decoded.equals(current)) {
                    return current; // No more decoding needed
                }
                current = decoded;
            } catch (IllegalArgumentException e) {
                // Malformed encoding
                return null;
            }
        }

        // Too many decoding iterations - likely malicious
        return null;
    }

    /**
     * Checks if the URI contains any protocol handler.
     */
    private boolean containsProtocolHandler(String uri) {
        // Common dangerous protocol handlers
        return uri.contains("://") ||
                uri.startsWith("javascript:") ||
                uri.startsWith("data:") ||
                uri.startsWith("vbscript:") ||
                uri.startsWith("file:") ||
                uri.startsWith("blob:") ||
                // Also check with whitespace variations (browser quirk)
                uri.matches(".*\\s*javascript\\s*:.*") ||
                uri.matches(".*\\s*data\\s*:.*");
    }

    /**
     * Checks if the string contains control characters or null bytes.
     */
    private boolean containsControlCharacters(String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            // Block ASCII control characters (0x00-0x1F) except tab, newline, carriage return
            // which are already filtered by other checks
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                return true;
            }
            // Block DEL character
            if (c == 0x7F) {
                return true;
            }
        }
        return false;
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        return value.replaceAll("[\\r\\n\\t]", "").substring(0, Math.min(value.length(), 64));
    }

    /**
     * Triggers background health data fetch for the logged-in user and their managed members.
     * This is fire-and-forget - errors are logged but don't affect the login flow.
     */
    private void triggerHealthDataFetch(MemberAccess memberAccess) {
        if (healthDataOrchestrator == null) {
            log.debug("HealthDataOrchestrator not available, skipping background fetch");
            return;
        }

        try {
            healthDataOrchestrator.triggerBackgroundFetchForSession(
                    memberAccess.eid(),
                    null,  // apiIdentifier - not available at login time
                    memberAccess.managedMembers()
            );
            log.debug("Triggered background health data fetch for user: {}", memberAccess.eid());
        } catch (Exception e) {
            log.warn("Failed to trigger background health data fetch: {}", e.getMessage());
        }
    }
}
