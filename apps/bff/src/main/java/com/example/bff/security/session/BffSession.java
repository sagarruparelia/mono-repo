package com.example.bff.security.session;

import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.MemberIdType;
import com.example.bff.security.context.Persona;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class BffSession implements Serializable {

    private String sessionId;
    private String hsidUuid;
    private String enterpriseId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private java.time.LocalDate birthDate;
    private String loggedInMemberIdValue;
    private MemberIdType loggedInMemberIdType;
    private Persona persona;
    private Set<DelegateType> delegateTypes;
    private Map<String, ManagedMember> managedMembersMap;
    private List<EligibilityPlan> eligibilities;
    private String accessToken;
    private String refreshToken;
    private Instant accessTokenExpiry;
    private Instant createdAt;
    private Instant lastAccessedAt;
    private String browserFingerprint;  // For session binding
    private String clientIp;            // For session binding

    public AuthContext toAuthContext() {
        return new AuthContext(
                enterpriseId,
                loggedInMemberIdValue,
                loggedInMemberIdType,
                persona,
                delegateTypes != null ? delegateTypes : Set.of(),
                managedMembersMap != null ? managedMembersMap : Map.of(),
                Set.of() // Active delegate types are set later for DELEGATE requests
        );
    }

    public BffSession touch() {
        this.lastAccessedAt = Instant.now();
        return this;
    }
}
