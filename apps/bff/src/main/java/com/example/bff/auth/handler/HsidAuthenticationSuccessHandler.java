package com.example.bff.auth.handler;

import com.example.bff.auth.model.TokenData;
import com.example.bff.auth.service.TokenOperations;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.authz.service.PermissionsFetchService;
import com.example.bff.common.util.ClientIpExtractor;
import com.example.bff.common.util.SessionCookieUtils;
import com.example.bff.common.util.StringSanitizer;
import com.example.bff.config.properties.SessionProperties;
import com.example.bff.health.service.HealthDataOrchestrator;
import com.example.bff.authz.exception.AgeRestrictionException;
import com.example.bff.authz.exception.NoAccessException;
import com.example.bff.authz.model.MemberAccess;
import com.example.bff.authz.service.MemberAccessOrchestrator;
import com.example.bff.session.model.ClientInfo;
import com.example.bff.session.service.SessionOperations;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class HsidAuthenticationSuccessHandler implements ServerAuthenticationSuccessHandler {

    private static final String REDIRECT_URI_COOKIE = "redirect_uri";
    private static final String AGE_RESTRICTED_PATH = "/error/age-restricted";
    private static final String NO_ACCESS_PATH = "/error/no-access";
    private static final String ERROR_PATH = "/error";

    private final SessionOperations sessionService;
    private final SessionProperties sessionProperties;
    private final MemberAccessOrchestrator memberAccessOrchestrator;
    @Nullable private final PermissionsFetchService permissionsFetchService;
    @Nullable private final TokenOperations tokenService;
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

        String hsidUuid = oidcUser.getSubject();
        log.info("Authentication success for hsidUuid: {}", hsidUuid);

        ClientInfo clientInfo = extractClientInfo(exchange.getExchange());
        Mono<OAuth2AuthorizedClient> authorizedClientMono = getAuthorizedClient(token);

        return sessionService.invalidateExistingSessions(hsidUuid)
                .then(memberAccessOrchestrator.resolveMemberAccess(hsidUuid))
                .flatMap(memberAccess -> createSessionAndRedirect(
                        exchange, hsidUuid, oidcUser, clientInfo, memberAccess, authorizedClientMono))
                .onErrorResume(AgeRestrictionException.class, e -> {
                    log.warn("hsidUuid {} failed age restriction check: {}", hsidUuid, e.getMessage());
                    return redirectToError(exchange.getExchange(), AGE_RESTRICTED_PATH);
                })
                .onErrorResume(NoAccessException.class, e -> {
                    log.warn("hsidUuid {} has no access: {}", hsidUuid, e.getMessage());
                    return redirectToError(exchange.getExchange(), NO_ACCESS_PATH);
                })
                .onErrorResume(e -> {
                    log.error("Failed to enrich member access for hsidUuid {}: {}", hsidUuid, e.getMessage(), e);
                    return redirectToError(exchange.getExchange(), ERROR_PATH);
                });
    }

    @NonNull
    private Mono<Void> createSessionAndRedirect(
            @NonNull WebFilterExchange exchange,
            @NonNull String hsidUuid,
            @NonNull OidcUser oidcUser,
            @NonNull ClientInfo clientInfo,
            @NonNull MemberAccess memberAccess,
            @NonNull Mono<OAuth2AuthorizedClient> authorizedClientMono) {

        log.info("Member access resolved for hsidUuid {}: persona={}, eligibility={}",
                hsidUuid, memberAccess.getEffectivePersona(), memberAccess.eligibilityStatus());

        Mono<PermissionSet> permissionsMono = fetchPermissions(hsidUuid, memberAccess);

        return Mono.zip(permissionsMono, authorizedClientMono.defaultIfEmpty(createEmptyClient()))
                .flatMap(tuple -> {
                    PermissionSet permissions = tuple.getT1();
                    OAuth2AuthorizedClient authorizedClient = tuple.getT2();

                    return sessionService.createSessionWithMemberAccess(
                            hsidUuid, oidcUser, clientInfo, memberAccess, permissions)
                            .flatMap(sessionId -> {
                                triggerHealthDataFetch(memberAccess);
                                return storeTokensAndRedirect(
                                        exchange.getExchange(), sessionId, authorizedClient, oidcUser);
                            });
                });
    }

    @NonNull
    private Mono<PermissionSet> fetchPermissions(@NonNull String hsidUuid, @NonNull MemberAccess memberAccess) {
        String persona = memberAccess.getEffectivePersona();

        if (permissionsFetchService == null) {
            log.debug("PermissionsFetchService not available, using empty permissions");
            return Mono.just(PermissionSet.empty(hsidUuid, persona));
        }

        return permissionsFetchService.fetchPermissions(hsidUuid)
                .doOnSuccess(p -> log.info("Fetched {} managed members for hsidUuid {} on login",
                        p.managedMembers() != null ? p.managedMembers().size() : 0, hsidUuid))
                .onErrorResume(e -> {
                    log.error("Failed to fetch permissions for hsidUuid {}: {}", hsidUuid, e.getMessage());
                    return Mono.just(PermissionSet.empty(hsidUuid, persona));
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
        String ipAddress = ClientIpExtractor.extractSimple(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        return ClientInfo.of(ipAddress, userAgent != null ? userAgent : "unknown");
    }

    @NonNull
    private Mono<Void> setSessionCookieAndRedirect(@NonNull ServerWebExchange exchange, @NonNull String sessionId) {
        SessionCookieUtils.addSessionCookie(exchange, sessionId, sessionProperties);

        String redirectUri = getRedirectUri(exchange);

        // Clear redirect cookie
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

    private boolean isValidRelativePath(@Nullable String uri) {
        if (uri == null || uri.isBlank()) {
            return false;
        }

        if (uri.length() > 2048) {
            return false;
        }

        String decoded = fullyDecodeUri(uri);
        if (decoded == null) {
            return false;
        }

        if (!decoded.startsWith("/") || decoded.startsWith("//")) {
            return false;
        }

        String lowerDecoded = decoded.toLowerCase();
        String lowerUri = uri.toLowerCase();

        if (containsProtocolHandler(lowerDecoded) || containsProtocolHandler(lowerUri)) {
            return false;
        }

        if (decoded.contains("\\") || uri.contains("\\")) {
            return false;
        }

        if (containsControlCharacters(decoded) || containsControlCharacters(uri)) {
            return false;
        }

        if (decoded.contains("//")) {
            return false;
        }

        if (decoded.contains("/../") || decoded.endsWith("/..")) {
            return false;
        }

        return true;
    }

    @Nullable
    private String fullyDecodeUri(@Nullable String uri) {
        if (uri == null) {
            return null;
        }

        String current = uri;
        int maxIterations = 10;

        for (int i = 0; i < maxIterations; i++) {
            try {
                String decoded = java.net.URLDecoder.decode(current, java.nio.charset.StandardCharsets.UTF_8);
                if (decoded.equals(current)) {
                    return current;
                }
                current = decoded;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        return null;
    }

    private boolean containsProtocolHandler(@NonNull String uri) {
        return uri.contains("://") ||
                uri.startsWith("javascript:") ||
                uri.startsWith("data:") ||
                uri.startsWith("vbscript:") ||
                uri.startsWith("file:") ||
                uri.startsWith("blob:") ||
                uri.matches(".*\\s*javascript\\s*:.*") ||
                uri.matches(".*\\s*data\\s*:.*");
    }

    private boolean containsControlCharacters(@NonNull String str) {
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                return true;
            }
            if (c == 0x7F) {
                return true;
            }
        }
        return false;
    }

    private void triggerHealthDataFetch(@NonNull MemberAccess memberAccess) {
        if (healthDataOrchestrator == null) {
            log.debug("HealthDataOrchestrator not available, skipping background fetch");
            return;
        }

        try {
            healthDataOrchestrator.triggerBackgroundFetchForSession(
                    memberAccess.enterpriseId(),
                    memberAccess.managedMembers()
            );
            log.debug("Triggered background health data fetch for user: {}", memberAccess.enterpriseId());
        } catch (Exception e) {
            log.warn("Failed to trigger background health data fetch: {}", e.getMessage());
        }
    }
}
