package com.example.bff.mfe.dto;

public record ProxyContext(
        String partnerId,
        String memberId,
        String persona,
        String operatorId,
        String operatorName,
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
