package com.example.bff.common.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for dual-auth endpoints that require member context.
 *
 * <p>All dual-auth endpoints use POST with this request body pattern
 * to specify which member's data is being accessed. This provides a
 * consistent interface for both HSID (session) and PROXY (partner) auth.</p>
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * @PostMapping("/health/summary")
 * public Mono<HealthSummary> getHealthSummary(
 *         @RequestBody @Valid MemberRequest request,
 *         ServerWebExchange exchange) {
 *     AuthContext auth = AuthContextResolver.require(exchange);
 *     // ABAC validates auth can access request.memberEid()
 *     return healthService.getSummary(request.memberEid());
 * }
 * }</pre>
 *
 * @param memberEid The member EID (Enterprise ID) whose data is being accessed
 */
public record MemberRequest(
        @NotBlank(message = "memberEid is required")
        String memberEid
) {
}
