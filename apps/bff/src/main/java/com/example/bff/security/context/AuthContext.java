package com.example.bff.security.context;

import com.example.bff.security.session.ManagedMember;

import java.util.Map;
import java.util.Set;

public record AuthContext(
        String enterpriseId,
        String loggedInMemberIdValue,
        MemberIdType loggedInMemberIdType,
        Persona persona,
        Set<DelegateType> delegateTypes,
        Map<String, ManagedMember> managedMembersMap,
        Set<DelegateType> activeDelegateTypes
) {
    public AuthContext withActiveEnterpriseId(String enterpriseId, Set<DelegateType> activeDelegateTypes) {
        return new AuthContext(
                enterpriseId,
                this.loggedInMemberIdValue,
                this.loggedInMemberIdType,
                this.persona,
                this.delegateTypes,
                this.managedMembersMap,
                activeDelegateTypes
        );
    }

    public static AuthContext forPartner(
            String enterpriseId,
            String loggedInMemberIdValue,
            MemberIdType loggedInMemberIdType,
            Persona persona) {
        return new AuthContext(
                enterpriseId,
                loggedInMemberIdValue,
                loggedInMemberIdType,
                persona,
                Set.of(),
                Map.of(),
                Set.of()
        );
    }
}
