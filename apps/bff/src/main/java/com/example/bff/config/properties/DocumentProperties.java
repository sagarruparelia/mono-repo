package com.example.bff.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.documents")
public record DocumentProperties(
        S3Properties s3,
        LimitsProperties limits
) {
    public record S3Properties(
            String bucketName,
            String region,
            String endpoint,
            String accessKeyId,
            String secretAccessKey
    ) {
        public S3Properties {
            if (region == null || region.isBlank()) {
                region = "us-east-1";
            }
        }
    }

    public record LimitsProperties(
            long maxFileSize,
            int maxFilesPerMember,
            List<String> allowedContentTypes
    ) {
        private static final List<String> DEFAULT_CONTENT_TYPES = List.of(
                "application/pdf",
                "image/jpeg",
                "image/png",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );

        public LimitsProperties {
            if (maxFileSize <= 0) {
                maxFileSize = 10 * 1024 * 1024; // 10MB default
            }
            if (maxFilesPerMember <= 0) {
                maxFilesPerMember = 50;
            }
            if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
                allowedContentTypes = DEFAULT_CONTENT_TYPES;
            }
        }
    }

    public DocumentProperties {
        if (s3 == null) {
            s3 = new S3Properties(null, "us-east-1", null, null, null);
        }
        if (limits == null) {
            limits = new LimitsProperties(10 * 1024 * 1024, 50, null);
        }
    }
}
