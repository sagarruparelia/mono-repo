package com.example.bff.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public Mono<ResponseEntity<Map<String, Object>>> getCurrentUser(
            @AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return Mono.just(ResponseEntity.ok(Map.of(
                    "authenticated", false
            )));
        }
        return Mono.just(ResponseEntity.ok(Map.of(
                "authenticated", true,
                "sub", user.getSubject(),
                "name", user.getFullName() != null ? user.getFullName() : "",
                "email", user.getEmail() != null ? user.getEmail() : ""
        )));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, Object>>> logout() {
        // Logout is handled by Spring Security's logout handler
        // This endpoint is for explicit logout requests
        return Mono.just(ResponseEntity.ok(Map.of(
                "message", "Logout initiated"
        )));
    }
}
