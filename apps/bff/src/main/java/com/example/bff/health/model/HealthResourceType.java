package com.example.bff.health.model;

// To add new type: add enum value, .graphql file, DTO, domain model, service method, controller endpoint
public enum HealthResourceType {

    IMMUNIZATION("immunization", "immunizations"),
    ALLERGY("allergy", "allergies");
    // Future: MEDICATION("medication", "medications"), CONDITION("condition", "conditions")

    private final String queryFileName;
    private final String dataFieldName;

    HealthResourceType(String queryFileName, String dataFieldName) {
        this.queryFileName = queryFileName;
        this.dataFieldName = dataFieldName;
    }

    public String getQueryFileName() {
        return queryFileName;
    }

    public String getDataFieldName() {
        return dataFieldName;
    }

    public String getCacheKey(String enterpriseId) {
        return queryFileName + ":" + enterpriseId;
    }
}
