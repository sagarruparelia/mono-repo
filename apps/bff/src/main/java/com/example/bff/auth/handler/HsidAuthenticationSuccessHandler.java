package com.example.bff.auth.handler;

import com.example.bff.auth.model.TokenData;
import com.example.bff.auth.service.TokenService;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.authz.service.PermissionsFetchService;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.SessionProperties;
import com.example.bff.health.service.HealthDataOrchestrator;
import com.example.bff.identity.exception.AgeRestrictionException;
import com.example.bff.identity.exception.NoAccessException;
import com.example.bff.identity.model.MemberAccess;
import com.example.bff.identity.service.MemberAccessOrchestrator;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/** Handles successful HSID authentication by enriching sessions with member access. */
@Slf4j
@Component
@RequiredArgsConstructor
public class HsidAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";
    private static final String REDIRECT_URI_COOKIE = "redirect_uri";
    private static final String AGE_RESTRICTED_PATH = "/error/age-restricted";
    private static final String NO_ACCESS_PATH = "/error/no-access";
    private static final String ERROR_PATH = "/error";

    private final SessionService sessionService;
    private final SessionProperties sessionProperties;
    private final MemberAccessOrchestrator memberAccessOrchestrator;
    @Nullable private final PermissionsFetchService permissionsFetchService;
    @Nullable private final TokenService tokenService;
    @Nullable private final ReactiveOAuth2AuthorizedClientService authorizedClientService;
    @Nullable private final HealthDataOrchestrator healthDataOrchestrator;

    @Override
    @NonNull
    public Mono<Void> onAuthenticationSuccess(@NonNull WebFilterExchange exchange,
                                               @NonNull Authentication authentication) {
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

    @NonNull
    private Mono<Void> createSessionAndRedirect(
            @NonNull WebFilterExchange exchange,
            @NonNull String userId,
            @NonNull OidcUser oidcUser,
            @NonNull ClientInfo clientInfo,
            @NonNull MemberAccess memberAccess,
            @NonNull Mono<OAuth2AuthorizedClient> authorizedClientMono) {

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

    @NonNull
    private Mono<PermissionSet> fetchPermissions(@NonNull String userId, @NonNull MemberAccess memberAccess) {
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

    @NonNull
    private Mono<Void> redirectToError(@NonNull ServerWebExchange exchange, @NonNull String errorPath) {
        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(URI.create(errorPath));
        return exchange.getResponse().setComplete();
    }

    @NonNull
    private Mono<OAuth2AuthorizedClient> getAuthorizedClient(@NonNull OAuth2AuthenticationToken token) {
        if (authorizedClientService == null) {
            log.debug("AuthorizedClientService not available, skipping token storage");
            return Mono.empty();
        }

        return authorizedClientService.loadAuthorizedClient(
                token.getAuthorizedClientRegistrationId(),
                token.getName()
        );
    }

    @Nullable
    private OAuth2AuthorizedClient createEmptyClient() {
        return null;
    }

    @NonNull
    private Mono<Void> storeTokensAndRedirect(
            @NonNull ServerWebExchange exchange,
            @NonNull String sessionId,
            @Nullable OAuth2AuthorizedClient authorizedClient,
            @NonNull OidcUser oidcUser) {

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
    private TokenData extractTokenData(@NonNull OAuth2AuthorizedClient authorizedClient, @NonNull OidcUser oidcUser) {
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

    @NonNull
    private ClientInfo extractClientInfo(@NonNull ServerWebExchange exchange) {
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

    @NonNull
    private Mono<Void> setSessionCookieAndRedirect(@NonNull ServerWebExchange exchange, @NonNull String sessionId) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(SESSION_COOKIE_NAME, sessionId)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(sessionProperties.timeout())
                .sameSite(sessionProperties.cookie().sameSite());

        // Set domain if configured (prevents subdomain takeover attacks)
        String domain = sessionProperties.cookie().domain();
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }

        exchange.getResponse().addCookie(builder.build());

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

    @NonNull
    private String getRedirectUri(@NonNull ServerWebExchange exchange) {
        HttpCookie redirectCookie = exchange.getRequest().getCookies().getFirst(REDIRECT_URI_COOKIE);
        if (redirectCookie != null && !redirectCookie.getValue().isBlank()) {
            String uri = redirectCookie.getValue();
            if (isValidRelativePath(uri)) {
                return uri;
            }
            log.warn("Rejected potentially malicious redirect URI: {}", StringSanitizer.forLog(uri));
        }
        return "/";
    }

    /** Validates URI is a safe relative path to prevent open redirect attacks. */
    private boolean isValidRelativePath(@Nullable String uri) {
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

    /** Recursively decodes URI to catch multi-layer encoding attacks. Returns null if malformed. */
    @Nullable
    private String fullyDecodeUri(@Nullable String uri) {
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

    private boolean containsProtocolHandler(@NonNull String uri) {
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

    private boolean containsControlCharacters(@NonNull String str) {
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

    /** Fire-and-forget health data fetch - errors don't affect login flow. */
    private void triggerHealthDataFetch(@NonNull MemberAccess memberAccess) {
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
