package com.example.bff.util;

import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.MemberIdType;
import com.example.bff.security.context.Persona;
import com.example.bff.security.session.ManagedMember;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Test builder for AuthContext.
 * Provides convenient methods to create AuthContext instances for testing.
 */
public class AuthContextTestBuilder {

    private String enterpriseId = "TEST-ENT-001";
    private String loggedInMemberIdValue = "test-hsid-uuid";
    private MemberIdType loggedInMemberIdType = MemberIdType.HSID;
    private Persona persona = Persona.SELF;
    private Set<DelegateType> delegateTypes = new HashSet<>();
    private Map<String, ManagedMember> managedMembersMap = new HashMap<>();
    private Set<DelegateType> activeDelegateTypes = new HashSet<>();

    public static AuthContextTestBuilder anAuthContext() {
        return new AuthContextTestBuilder();
    }

    public static AuthContext aSelfAuthContext() {
        return anAuthContext()
                .withPersona(Persona.SELF)
                .build();
    }

    public static AuthContext aDelegateAuthContext() {
        return anAuthContext()
                .withPersona(Persona.DELEGATE)
                .withDelegateTypes(Set.of(DelegateType.RPR, DelegateType.DAA))
                .withManagedMember(ManagedMemberTestBuilder.aManagedMember().build())
                .build();
    }

    public static AuthContext anAgentAuthContext() {
        return anAuthContext()
                .withPersona(Persona.AGENT)
                .withLoggedInMemberIdType(MemberIdType.MSID)
                .withLoggedInMemberIdValue("agent-msid-123")
                .build();
    }

    public AuthContextTestBuilder withEnterpriseId(String enterpriseId) {
        this.enterpriseId = enterpriseId;
        return this;
    }

    public AuthContextTestBuilder withLoggedInMemberIdValue(String loggedInMemberIdValue) {
        this.loggedInMemberIdValue = loggedInMemberIdValue;
        return this;
    }

    public AuthContextTestBuilder withLoggedInMemberIdType(MemberIdType loggedInMemberIdType) {
        this.loggedInMemberIdType = loggedInMemberIdType;
        return this;
    }

    public AuthContextTestBuilder withPersona(Persona persona) {
        this.persona = persona;
        return this;
    }

    public AuthContextTestBuilder withDelegateTypes(Set<DelegateType> delegateTypes) {
        this.delegateTypes = new HashSet<>(delegateTypes);
        return this;
    }

    public AuthContextTestBuilder withManagedMembersMap(Map<String, ManagedMember> managedMembersMap) {
        this.managedMembersMap = new HashMap<>(managedMembersMap);
        return this;
    }

    public AuthContextTestBuilder withManagedMember(ManagedMember managedMember) {
        this.managedMembersMap.put(managedMember.enterpriseId(), managedMember);
        return this;
    }

    public AuthContextTestBuilder withActiveDelegateTypes(Set<DelegateType> activeDelegateTypes) {
        this.activeDelegateTypes = new HashSet<>(activeDelegateTypes);
        return this;
    }

    public AuthContext build() {
        return new AuthContext(
                enterpriseId,
                loggedInMemberIdValue,
                loggedInMemberIdType,
                persona,
                delegateTypes,
                managedMembersMap,
                activeDelegateTypes
        );
    }

    /**
     * Nested builder for ManagedMember.
     */
    public static class ManagedMemberTestBuilder {
        private String enterpriseId = "MANAGED-ENT-001";
        private String firstName = "Managed";
        private String lastName = "Member";
        private LocalDate birthDate = LocalDate.of(1990, 1, 15);
        private Set<DelegateType> delegateTypes = Set.of(DelegateType.RPR, DelegateType.DAA);

        public static ManagedMemberTestBuilder aManagedMember() {
            return new ManagedMemberTestBuilder();
        }

        public ManagedMemberTestBuilder withEnterpriseId(String enterpriseId) {
            this.enterpriseId = enterpriseId;
            return this;
        }

        public ManagedMemberTestBuilder withFirstName(String firstName) {
            this.firstName = firstName;
            return this;
        }

        public ManagedMemberTestBuilder withLastName(String lastName) {
            this.lastName = lastName;
            return this;
        }

        public ManagedMemberTestBuilder withBirthDate(LocalDate birthDate) {
            this.birthDate = birthDate;
            return this;
        }

        public ManagedMemberTestBuilder withDelegateTypes(Set<DelegateType> delegateTypes) {
            this.delegateTypes = delegateTypes;
            return this;
        }

        public ManagedMember build() {
            return new ManagedMember(
                    enterpriseId,
                    firstName,
                    lastName,
                    birthDate,
                    delegateTypes);
        }
    }
}
