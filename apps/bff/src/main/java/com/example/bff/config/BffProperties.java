package com.example.bff.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "bff")
public class BffProperties {

    private Session session = new Session();
    private Cache cache = new Cache();
    private Client client = new Client();
    private Eligibility eligibility = new Eligibility();
    private HealthCache healthCache = new HealthCache();
    private Document document = new Document();

    @Data
    public static class Session {
        private String store = "in-memory";  // "in-memory" or "redis"
        private int timeoutMinutes = 30;
        private String cookieName = "BFF_SESSION";
        private String cookieDomain = "abc.com";  // Use leading dot for subdomain support
        private boolean cookieSecure = true;
        private boolean cookieHttpOnly = true;
        private String cookieSameSite = "Strict";
        private List<String> allowedOrigins = List.of("https://abc.com", "https://www.abc.com");
        private String csrfCookieName = "XSRF-TOKEN";  // HttpOnly=false so JS can read
        private String csrfHeaderName = "X-CSRF-TOKEN";
        private boolean sessionBindingEnabled = true;  // Validate fingerprint + IP
        private boolean strictSessionBinding = true;   // Reject on mismatch (false = permissive/log only)
    }

    @Data
    public static class Cache {
        private String store = "in-memory";  // "in-memory" or "redis"
        private int ttlMinutes = 30;
    }

    @Data
    public static class Client {
        private ServiceConfig userService = new ServiceConfig();
        private ServiceConfig delegateGraph = new ServiceConfig();
        private ServiceConfig eligibilityGraph = new ServiceConfig();
        private ServiceConfig ecdhGraph = new ServiceConfig();
    }

    @Data
    public static class ServiceConfig {
        private String baseUrl;
    }

    @Data
    public static class Eligibility {
        private List<String> eligiblePlanCodes = List.of();
    }

    @Data
    public static class HealthCache {
        private int ttlHours = 2;
        private boolean backgroundBuildEnabled = true;
    }

    @Data
    public static class Document {
        private String s3Bucket;
        private String s3Region = "us-east-1";
        private int presignedUploadTtlMinutes = 15;
        private int presignedDownloadTtlMinutes = 5;
        private int maxFileSizeMb = 25;
        private int maxConcurrentUploads = 4;
        private int tempExpiryHours = 4;
        private String kmsKeyId;
        private List<String> allowedContentTypes = List.of(
                "application/pdf",
                "image/jpeg",
                "image/png",
                "image/tiff",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
    }
}
