package com.example.bff.identity.controller;

import com.example.bff.auth.model.AuthPrincipal;
import com.example.bff.auth.model.Persona;
import com.example.bff.authz.annotation.RequirePersona;
import com.example.bff.authz.model.ManagedMemberAccess;
import com.example.bff.authz.model.PermissionSet;
import com.example.bff.identity.dto.ManagedMemberDetailResponse;
import com.example.bff.identity.model.ManagedMember;
import com.example.bff.session.service.SessionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for access-related endpoints.
 *
 * <p>Provides endpoints for retrieving information about members
 * the logged-in user has permission to act on behalf of.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/access")
@RequiredArgsConstructor
public class AccessController {

    private static final String SESSION_COOKIE_NAME = "BFF_SESSION";

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    /**
     * Get detailed information about managed members including permission details.
     *
     * <p>Returns a list of members the logged-in user can act on behalf of,
     * with their personal information and permission validity dates.</p>
     *
     * @param exchange The server web exchange
     * @return List of managed member details with permissions
     */
    @GetMapping("/managed-members")
    @RequirePersona(Persona.DELEGATE)
    public Mono<ResponseEntity<List<ManagedMemberDetailResponse>>> getManagedMembers(
            ServerWebExchange exchange) {

        AuthPrincipal principal = exchange.getAttribute(AuthPrincipal.EXCHANGE_ATTRIBUTE);
        if (principal == null) {
            log.warn("No AuthPrincipal found in exchange");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        PermissionSet permissions = principal.permissions();
        if (permissions == null || !permissions.hasManagedMembers()) {
            log.debug("No managed members in permissions for user {}", principal.loggedInMemberIdValue());
            return Mono.just(ResponseEntity.ok(List.of()));
        }

        // Get session to retrieve detailed member info (firstName, lastName, birthDate)
        HttpCookie sessionCookie = exchange.getRequest().getCookies().getFirst(SESSION_COOKIE_NAME);
        if (sessionCookie == null || sessionCookie.getValue().isBlank()) {
            log.warn("No session cookie found");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        String sessionId = sessionCookie.getValue();

        return sessionService.getSession(sessionId)
                .map(session -> {
                    // Parse managed members from session for detailed info
                    Map<String, ManagedMember> memberMap = parseManagedMembersToMap(
                            session.managedMembersJson());

                    // Build response combining identity data with permission details
                    List<ManagedMemberDetailResponse> responses = permissions.getViewableManagedMembers()
                            .stream()
                            .map(access -> buildResponse(access, memberMap))
                            .toList();

                    log.debug("Returning {} managed members for user {}",
                            responses.size(), principal.loggedInMemberIdValue());
                    return ResponseEntity.ok(responses);
                })
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    /**
     * Build response by combining ManagedMemberAccess (permissions) with ManagedMember (identity info).
     */
    private ManagedMemberDetailResponse buildResponse(
            ManagedMemberAccess access,
            Map<String, ManagedMember> memberMap) {

        ManagedMember member = memberMap.get(access.memberId());

        if (member != null) {
            return ManagedMemberDetailResponse.from(
                    access.memberId(),
                    member.firstName(),
                    member.lastName(),
                    member.birthDate(),
                    access.permissions()
            );
        }

        // Fallback: use memberName from access if no detailed member info
        log.debug("No detailed member info found for {}, using access data", access.memberId());
        return ManagedMemberDetailResponse.from(
                access.memberId(),
                null,
                access.memberName(),
                null,
                access.permissions()
        );
    }

    /**
     * Parse managed members JSON to a map by enterprise ID for quick lookup.
     */
    private Map<String, ManagedMember> parseManagedMembersToMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            List<ManagedMember> members = objectMapper.readValue(
                    json, new TypeReference<List<ManagedMember>>() {});
            Map<String, ManagedMember> map = new HashMap<>();
            for (ManagedMember member : members) {
                map.put(member.enterpriseId(), member);
            }
            return map;
        } catch (Exception e) {
            log.warn("Failed to parse managed members JSON: {}", e.getMessage());
            return Map.of();
        }
    }
}
