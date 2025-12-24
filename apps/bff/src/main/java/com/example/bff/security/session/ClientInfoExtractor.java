package com.example.bff.security.session;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;

@Component
public class ClientInfoExtractor {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_FINGERPRINT = "X-Fingerprint";

    // X-Forwarded-For first (ALB/proxy), fallback to direct connection
    public String extractClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst(X_FORWARDED_FOR);
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For contains comma-separated IPs: client, proxy1, proxy2, ...
            // The first IP is the original client
            return xff.split(",")[0].trim();
        }

        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null ? remote.getHostString() : null;
    }

    public String extractFingerprint(ServerHttpRequest request) {
        return request.getHeaders().getFirst(X_FINGERPRINT);
    }
}
