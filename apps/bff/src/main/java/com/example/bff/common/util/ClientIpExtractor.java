package com.example.bff.common.util;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.regex.Pattern;

/**
 * Utility for extracting client IP addresses from requests.
 * Handles X-Forwarded-For header with trusted proxy validation.
 */
public final class ClientIpExtractor {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile(
            "^([0-9]{1,3}\\.){3}[0-9]{1,3}$|^([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}$");

    private ClientIpExtractor() {}

    /**
     * Extracts client IP from exchange, using trusted proxy validation.
     * Only trusts X-Forwarded-For if the direct connection is from a trusted proxy.
     *
     * @param exchange the server web exchange
     * @return the client IP address, or "unknown" if not determinable
     */
    @NonNull
    public static String extract(@NonNull ServerWebExchange exchange) {
        return extract(exchange.getRequest());
    }

    /**
     * Extracts client IP from request, using trusted proxy validation.
     *
     * @param request the server HTTP request
     * @return the client IP address, or "unknown" if not determinable
     */
    @NonNull
    public static String extract(@NonNull ServerHttpRequest request) {
        InetSocketAddress remoteAddress = request.getRemoteAddress();
        String directIp = remoteAddress != null && remoteAddress.getAddress() != null
                ? remoteAddress.getAddress().getHostAddress()
                : "unknown";

        // Only trust forwarded headers if request came through trusted proxy
        if (isTrustedProxy(directIp)) {
            String forwardedFor = request.getHeaders().getFirst(X_FORWARDED_FOR);
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                // Take the rightmost untrusted IP (client IP added by first proxy)
                String[] ips = forwardedFor.split(",");
                for (int i = ips.length - 1; i >= 0; i--) {
                    String ip = ips[i].trim();
                    if (!isTrustedProxy(ip) && isValidIp(ip)) {
                        return ip;
                    }
                }
                // All IPs in chain are trusted proxies - use first as client
                String firstIp = ips[0].trim();
                if (isValidIp(firstIp)) {
                    return firstIp;
                }
            }
        }
        return directIp;
    }

    /**
     * Simple extraction without trusted proxy validation.
     * Takes first IP from X-Forwarded-For header.
     * Use this for logging where strict security isn't required.
     *
     * @param exchange the server web exchange
     * @return the client IP address, or "unknown" if not determinable
     */
    @NonNull
    public static String extractSimple(@NonNull ServerWebExchange exchange) {
        return extractSimple(exchange.getRequest());
    }

    /**
     * Simple extraction without trusted proxy validation.
     *
     * @param request the server HTTP request
     * @return the client IP address, or "unknown" if not determinable
     */
    @NonNull
    public static String extractSimple(@NonNull ServerHttpRequest request) {
        String forwardedFor = request.getHeaders().getFirst(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String firstIp = forwardedFor.split(",")[0].trim();
            if (isValidIp(firstIp)) {
                return firstIp;
            }
        }

        InetSocketAddress remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null) {
            InetAddress address = remoteAddress.getAddress();
            if (address != null) {
                return address.getHostAddress();
            }
        }

        return "unknown";
    }

    /**
     * Validates that a string is a valid IP address format.
     *
     * @param ip the IP string to validate
     * @return true if valid IPv4 or IPv6 format
     */
    public static boolean isValidIp(@Nullable String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        return IP_ADDRESS_PATTERN.matcher(ip).matches();
    }

    /**
     * Checks if an IP address is from a trusted proxy (private network).
     * Trusted: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, localhost, IPv6 link-local
     *
     * @param ip the IP address to check
     * @return true if the IP is from a trusted private network
     */
    public static boolean isTrustedProxy(@Nullable String ip) {
        if (ip == null) {
            return false;
        }

        // IPv4 private ranges
        if (ip.startsWith("10.") || ip.startsWith("192.168.") ||
                ip.equals("127.0.0.1") || ip.equals("::1")) {
            return true;
        }

        // Check 172.16.0.0/12 range (172.16.x.x - 172.31.x.x)
        if (ip.startsWith("172.")) {
            String[] octets = ip.split("\\.");
            if (octets.length >= 2) {
                try {
                    int secondOctet = Integer.parseInt(octets[1]);
                    return secondOctet >= 16 && secondOctet <= 31;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }

        // IPv6 private ranges (link-local and unique local)
        return ip.startsWith("fe80:") || ip.startsWith("fc") || ip.startsWith("fd");
    }
}
