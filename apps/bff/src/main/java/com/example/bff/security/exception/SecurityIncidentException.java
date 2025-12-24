package com.example.bff.security.exception;

import lombok.Getter;

// Security incident (e.g., unauthorized access attempt). Should be logged and monitored.
@Getter
public class SecurityIncidentException extends RuntimeException {

    private final String loggedInMemberIdValue;
    private final String attemptedEnterpriseId;
    private final String incidentType;

    public SecurityIncidentException(String message, String loggedInMemberIdValue, String attemptedEnterpriseId) {
        super(message);
        this.loggedInMemberIdValue = loggedInMemberIdValue;
        this.attemptedEnterpriseId = attemptedEnterpriseId;
        this.incidentType = "UNAUTHORIZED_ACCESS_ATTEMPT";
    }

    public SecurityIncidentException(String message, String loggedInMemberIdValue,
            String attemptedEnterpriseId, String incidentType) {
        super(message);
        this.loggedInMemberIdValue = loggedInMemberIdValue;
        this.attemptedEnterpriseId = attemptedEnterpriseId;
        this.incidentType = incidentType;
    }
}
