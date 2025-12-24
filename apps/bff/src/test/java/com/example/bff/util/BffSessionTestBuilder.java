package com.example.bff.util;

import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.MemberIdType;
import com.example.bff.security.context.Persona;
import com.example.bff.security.session.BffSession;
import com.example.bff.security.session.EligibilityPlan;
import com.example.bff.security.session.ManagedMember;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Test builder for BffSession.
 * Provides convenient methods to create BffSession instances for testing.
 */
public class BffSessionTestBuilder {

    private String sessionId = UUID.randomUUID().toString();
    private String hsidUuid = "test-hsid-uuid-" + UUID.randomUUID().toString().substring(0, 8);
    private String enterpriseId = "TEST-ENT-001";
    private String firstName = "Test";
    private String lastName = "User";
    private String email = "test.user@example.com";
    private String phone = "555-123-4567";
    private LocalDate birthDate = LocalDate.of(1985, 6, 15);
    private String loggedInMemberIdValue;
    private MemberIdType loggedInMemberIdType = MemberIdType.HSID;
    private Persona persona = Persona.SELF;
    private Set<DelegateType> delegateTypes = new HashSet<>();
    private Map<String, ManagedMember> managedMembersMap = new HashMap<>();
    private List<EligibilityPlan> eligibilities = List.of();
    private String accessToken = "test-access-token";
    private String refreshToken = "test-refresh-token";
    private Instant accessTokenExpiry = Instant.now().plusSeconds(3600);
    private Instant createdAt = Instant.now();
    private Instant lastAccessedAt = Instant.now();
    private String browserFingerprint = "test-fingerprint-hash";
    private String clientIp = "127.0.0.1";

    public static BffSessionTestBuilder aBffSession() {
        return new BffSessionTestBuilder();
    }

    public static BffSession aSelfSession() {
        return aBffSession()
                .withPersona(Persona.SELF)
                .build();
    }

    public static BffSession aDelegateSession() {
        ManagedMember managedMember = AuthContextTestBuilder.ManagedMemberTestBuilder
                .aManagedMember()
                .build();

        return aBffSession()
                .withPersona(Persona.DELEGATE)
                .withDelegateTypes(Set.of(DelegateType.RPR, DelegateType.DAA))
                .withManagedMember(managedMember)
                .build();
    }

    public static BffSession anEligibleSession() {
        return aBffSession()
                .withPersona(Persona.SELF)
                .withEligibilities(List.of(
                        new EligibilityPlan(
                                "TEST001",
                                "MEM-001",
                                LocalDate.now().minusMonths(6),
                                null)
                ))
                .build();
    }

    public BffSessionTestBuilder withSessionId(String sessionId) {
        this.sessionId = sessionId;
        return this;
    }

    public BffSessionTestBuilder withHsidUuid(String hsidUuid) {
        this.hsidUuid = hsidUuid;
        return this;
    }

    public BffSessionTestBuilder withEnterpriseId(String enterpriseId) {
        this.enterpriseId = enterpriseId;
        return this;
    }

    public BffSessionTestBuilder withFirstName(String firstName) {
        this.firstName = firstName;
        return this;
    }

    public BffSessionTestBuilder withLastName(String lastName) {
        this.lastName = lastName;
        return this;
    }

    public BffSessionTestBuilder withEmail(String email) {
        this.email = email;
        return this;
    }

    public BffSessionTestBuilder withPhone(String phone) {
        this.phone = phone;
        return this;
    }

    public BffSessionTestBuilder withBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
        return this;
    }

    public BffSessionTestBuilder withLoggedInMemberIdValue(String loggedInMemberIdValue) {
        this.loggedInMemberIdValue = loggedInMemberIdValue;
        return this;
    }

    public BffSessionTestBuilder withLoggedInMemberIdType(MemberIdType loggedInMemberIdType) {
        this.loggedInMemberIdType = loggedInMemberIdType;
        return this;
    }

    public BffSessionTestBuilder withPersona(Persona persona) {
        this.persona = persona;
        return this;
    }

    public BffSessionTestBuilder withDelegateTypes(Set<DelegateType> delegateTypes) {
        this.delegateTypes = new HashSet<>(delegateTypes);
        return this;
    }

    public BffSessionTestBuilder withManagedMembersMap(Map<String, ManagedMember> managedMembersMap) {
        this.managedMembersMap = new HashMap<>(managedMembersMap);
        return this;
    }

    public BffSessionTestBuilder withManagedMember(ManagedMember managedMember) {
        this.managedMembersMap.put(managedMember.enterpriseId(), managedMember);
        return this;
    }

    public BffSessionTestBuilder withEligibilities(List<EligibilityPlan> eligibilities) {
        this.eligibilities = eligibilities;
        return this;
    }

    public BffSessionTestBuilder withAccessToken(String accessToken) {
        this.accessToken = accessToken;
        return this;
    }

    public BffSessionTestBuilder withRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
        return this;
    }

    public BffSessionTestBuilder withAccessTokenExpiry(Instant accessTokenExpiry) {
        this.accessTokenExpiry = accessTokenExpiry;
        return this;
    }

    public BffSessionTestBuilder withCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public BffSessionTestBuilder withLastAccessedAt(Instant lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
        return this;
    }

    public BffSessionTestBuilder withBrowserFingerprint(String browserFingerprint) {
        this.browserFingerprint = browserFingerprint;
        return this;
    }

    public BffSessionTestBuilder withClientIp(String clientIp) {
        this.clientIp = clientIp;
        return this;
    }

    public BffSessionTestBuilder withExpiredAccessToken() {
        this.accessTokenExpiry = Instant.now().minusSeconds(60);
        return this;
    }

    public BffSession build() {
        String memberIdValue = loggedInMemberIdValue != null ? loggedInMemberIdValue : hsidUuid;

        return BffSession.builder()
                .sessionId(sessionId)
                .hsidUuid(hsidUuid)
                .enterpriseId(enterpriseId)
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .phone(phone)
                .birthDate(birthDate)
                .loggedInMemberIdValue(memberIdValue)
                .loggedInMemberIdType(loggedInMemberIdType)
                .persona(persona)
                .delegateTypes(delegateTypes)
                .managedMembersMap(managedMembersMap)
                .eligibilities(eligibilities)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .accessTokenExpiry(accessTokenExpiry)
                .createdAt(createdAt)
                .lastAccessedAt(lastAccessedAt)
                .browserFingerprint(browserFingerprint)
                .clientIp(clientIp)
                .build();
    }
}
