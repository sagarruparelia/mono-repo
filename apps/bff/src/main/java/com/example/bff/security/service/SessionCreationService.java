package com.example.bff.security.service;

import com.example.bff.client.delegate.DelegateGraphClient;
import com.example.bff.client.eligibility.EligibilityGraphClient;
import com.example.bff.client.userservice.UserInfo;
import com.example.bff.client.userservice.UserServiceClient;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.MemberIdType;
import com.example.bff.security.context.Persona;
import com.example.bff.security.exception.AuthorizationException;
import com.example.bff.security.session.BffSession;
import com.example.bff.security.session.EligibilityPlan;
import com.example.bff.security.session.ManagedMember;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionCreationService {

    private static final int MINIMUM_AGE = 13;
    private static final int DELEGATE_MINIMUM_AGE = 18;
    private static final String PRIMARY_MEMBER_TYPE = "PR";

    private final UserServiceClient userServiceClient;
    private final DelegateGraphClient delegateGraphClient;
    private final EligibilityGraphClient eligibilityGraphClient;

    public Mono<BffSession> createSession(TokenResponse tokens, String clientIp, String browserFingerprint) {
        String hsidUuid = JwtUtils.extractSubClaim(tokens.idToken());
        log.debug("Creating session for hsidUuid: {}, clientIp: {}, fingerprint: {}",
                hsidUuid, clientIp, browserFingerprint != null ? "present" : "null");

        return userServiceClient.readUser(hsidUuid)
                .flatMap(userInfo -> validateAndBuildSession(userInfo, tokens, hsidUuid, clientIp, browserFingerprint));
    }

    private Mono<BffSession> validateAndBuildSession(
            UserInfo userInfo, TokenResponse tokens, String hsidUuid,
            String clientIp, String browserFingerprint) {
        int age = calculateAge(userInfo.birthDate());

        // Age restriction: 12 and under blocked
        if (age < MINIMUM_AGE) {
            log.warn("Access denied for user under {}: enterpriseId={}", MINIMUM_AGE, userInfo.enterpriseId());
            return Mono.error(new AuthorizationException(
                    "Access denied: Age restriction (under " + MINIMUM_AGE + ")"));
        }

        // For PR member type, fetch delegate and eligibility data in parallel
        if (PRIMARY_MEMBER_TYPE.equals(userInfo.memberType())) {
            return enrichWithDelegateAndEligibility(userInfo, tokens, hsidUuid, age, clientIp, browserFingerprint);
        }

        // Non-PR members: only check eligibility
        return fetchEligibilityOnly(userInfo, tokens, hsidUuid, age, clientIp, browserFingerprint);
    }

    private Mono<BffSession> enrichWithDelegateAndEligibility(
            UserInfo userInfo, TokenResponse tokens, String hsidUuid, int age,
            String clientIp, String browserFingerprint) {

        Mono<List<ManagedMember>> managedMembersMono = delegateGraphClient
                .getManagedMembers(userInfo.enterpriseId())
                .collectList()
                .cache();

        Mono<List<EligibilityPlan>> eligiblePlansMono = eligibilityGraphClient
                .getEligiblePlans(userInfo.apiIdentifier())
                .collectList()
                .cache();

        return Mono.zip(managedMembersMono, eligiblePlansMono)
                .flatMap(tuple -> determinePersonaAndBuildSession(
                        userInfo, tokens, hsidUuid, age, tuple.getT1(), tuple.getT2(),
                        clientIp, browserFingerprint));
    }

    private Mono<BffSession> fetchEligibilityOnly(
            UserInfo userInfo, TokenResponse tokens, String hsidUuid, int age,
            String clientIp, String browserFingerprint) {

        return eligibilityGraphClient.getEligiblePlans(userInfo.apiIdentifier())
                .collectList()
                .flatMap(eligibilities -> determinePersonaAndBuildSession(
                        userInfo, tokens, hsidUuid, age, List.of(), eligibilities,
                        clientIp, browserFingerprint));
    }

    private Mono<BffSession> determinePersonaAndBuildSession(
            UserInfo userInfo, TokenResponse tokens, String hsidUuid, int age,
            List<ManagedMember> managedMembers, List<EligibilityPlan> eligibilities,
            String clientIp, String browserFingerprint) {

        // Has eligible plan -> SELF
        if (!eligibilities.isEmpty()) {
            log.debug("User has {} eligible plans, persona=SELF, enterpriseId={}",
                    eligibilities.size(), userInfo.enterpriseId());
            return Mono.just(buildSession(userInfo, tokens, hsidUuid, Persona.SELF,
                    null, eligibilities, clientIp, browserFingerprint));
        }

        // Age >= 18 + valid delegates with DAA+ROI -> DELEGATE
        if (age >= DELEGATE_MINIMUM_AGE && hasValidDelegatesWithRoi(managedMembers)) {
            Map<String, ManagedMember> managedMembersMap = managedMembers.stream()
                    .filter(m -> m.delegateTypes().contains(DelegateType.DAA) &&
                            m.delegateTypes().contains(DelegateType.ROI))
                    .collect(Collectors.toMap(ManagedMember::enterpriseId, Function.identity()));

            log.debug("User has {} managed members with DAA+ROI, persona=DELEGATE, enterpriseId={}",
                    managedMembersMap.size(), userInfo.enterpriseId());
            return Mono.just(buildSession(userInfo, tokens, hsidUuid, Persona.DELEGATE,
                    managedMembersMap, eligibilities, clientIp, browserFingerprint));
        }

        // Otherwise blocked
        log.warn("No eligible persona for user: enterpriseId={}, age={}, eligibilities={}, managedMembers={}",
                userInfo.enterpriseId(), age, eligibilities.size(), managedMembers.size());
        return Mono.error(new AuthorizationException(
                "Access denied: No eligible plan and no valid delegate relationships"));
    }

    private BffSession buildSession(
            UserInfo userInfo, TokenResponse tokens, String hsidUuid,
            Persona persona, Map<String, ManagedMember> managedMembersMap,
            List<EligibilityPlan> eligibilities, String clientIp, String browserFingerprint) {

        Set<DelegateType> allDelegateTypes = extractAllDelegateTypes(managedMembersMap);

        return BffSession.builder()
                .sessionId(UUID.randomUUID().toString())
                .hsidUuid(hsidUuid)
                .enterpriseId(userInfo.enterpriseId())
                .firstName(userInfo.firstName())
                .lastName(userInfo.lastName())
                .email(userInfo.email())
                .phone(userInfo.phone())
                .birthDate(userInfo.birthDate())
                .loggedInMemberIdValue(hsidUuid)
                .loggedInMemberIdType(MemberIdType.HSID)
                .persona(persona)
                .delegateTypes(allDelegateTypes)
                .managedMembersMap(managedMembersMap != null ? managedMembersMap : Map.of())
                .eligibilities(eligibilities)
                .accessToken(tokens.accessToken())
                .refreshToken(tokens.refreshToken())
                .accessTokenExpiry(tokens.expiresAt())
                .createdAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .clientIp(clientIp)
                .browserFingerprint(browserFingerprint)
                .build();
    }

    private boolean hasValidDelegatesWithRoi(List<ManagedMember> managedMembers) {
        return managedMembers.stream()
                .anyMatch(m -> m.delegateTypes().contains(DelegateType.DAA) &&
                        m.delegateTypes().contains(DelegateType.ROI));
    }

    private Set<DelegateType> extractAllDelegateTypes(Map<String, ManagedMember> managedMembersMap) {
        if (managedMembersMap == null || managedMembersMap.isEmpty()) {
            return Set.of();
        }

        return managedMembersMap.values().stream()
                .flatMap(m -> m.delegateTypes().stream())
                .collect(Collectors.toSet());
    }

    private int calculateAge(LocalDate birthDate) {
        if (birthDate == null) {
            return 0;
        }
        return Period.between(birthDate, LocalDate.now()).getYears();
    }
}
