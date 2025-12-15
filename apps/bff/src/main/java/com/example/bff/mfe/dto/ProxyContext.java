package com.example.bff.mfe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Proxy context containing partner and member information.
 * Used for MFE proxy requests where context comes from JWT claims or headers.
 */
public record ProxyContext(
        @NotBlank(message = "Partner ID is required")
        @Size(min = 1, max = 100, message = "Partner ID must be between 1 and 100 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Partner ID must contain only alphanumeric characters, hyphens, and underscores")
        String partnerId,

        @NotBlank(message = "Member ID is required")
        @Size(min = 1, max = 100, message = "Member ID must be between 1 and 100 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Member ID must contain only alphanumeric characters, hyphens, and underscores")
        String memberId,

        @NotBlank(message = "Persona is required")
        @Pattern(regexp = "^(individual|parent|agent|case_worker|config)$", message = "Persona must be one of: individual, parent, agent, case_worker, config")
        String persona,

        @Size(max = 100, message = "Operator ID must not exceed 100 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Operator ID must contain only alphanumeric characters, hyphens, and underscores")
        String operatorId,

        @Size(max = 200, message = "Operator name must not exceed 200 characters")
        String operatorName,

        @Size(max = 100, message = "Correlation ID must not exceed 100 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Correlation ID must contain only alphanumeric characters, hyphens, and underscores")
        String correlationId
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String partnerId;
        private String memberId;
        private String persona;
        private String operatorId;
        private String operatorName;
        private String correlationId;

        public Builder partnerId(String partnerId) {
            this.partnerId = partnerId;
            return this;
        }

        public Builder memberId(String memberId) {
            this.memberId = memberId;
            return this;
        }

        public Builder persona(String persona) {
            this.persona = persona;
            return this;
        }

        public Builder operatorId(String operatorId) {
            this.operatorId = operatorId;
            return this;
        }

        public Builder operatorName(String operatorName) {
            this.operatorName = operatorName;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public ProxyContext build() {
            return new ProxyContext(partnerId, memberId, persona, operatorId, operatorName, correlationId);
        }
    }
}
