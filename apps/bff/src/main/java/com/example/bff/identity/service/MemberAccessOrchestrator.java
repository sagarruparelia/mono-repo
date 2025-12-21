package com.example.bff.identity.service;

import com.example.bff.common.util.StringSanitizer;
import com.example.bff.identity.dto.UserInfoResponse;
import com.example.bff.identity.exception.AgeRestrictionException;
import com.example.bff.identity.exception.IdentityServiceException;
import com.example.bff.identity.exception.NoAccessException;
import com.example.bff.identity.model.ManagedMember;
import com.example.bff.identity.model.EligibilityResult;
import com.example.bff.identity.model.MemberAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.List;

import static com.example.bff.identity.model.MemberAccess.ADULT_AGE;
import static com.example.bff.identity.model.MemberAccess.MINIMUM_ACCESS_AGE;

/**
 * Orchestrates the member access enrichment flow after HSID login.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberAccessOrchestrator {

    private final UserInfoService userInfoService;
    private final EligibilityService eligibilityService;
    private final ManagedMembersService managedMembersService;

    @NonNull
    public Mono<MemberAccess> resolveMemberAccess(@NonNull String hsidUuid) {
        log.info("Resolving member access for hsidUuid: {}", StringSanitizer.forLog(hsidUuid));

        return userInfoService.getUserInfo(hsidUuid)
                .flatMap(userInfo -> processUserInfo(hsidUuid, userInfo))
                .doOnSuccess(access -> log.info(
                        "Member access resolved for hsidUuid: {}, persona: {}, canAccess: {}",
                        StringSanitizer.forLog(hsidUuid), access.getEffectivePersona(), access.canAccessSystem()))
                .doOnError(e -> log.error(
                        "Failed to resolve member access for hsidUuid {}: {}", StringSanitizer.forLog(hsidUuid), e.getMessage()));
    }

    private Mono<MemberAccess> processUserInfo(String hsidUuid, UserInfoResponse userInfo) {
        // Extract required fields
        String eid = userInfo.getEnterpriseId();
        if (eid == null || eid.isBlank()) {
            return Mono.error(new IdentityServiceException(
                    "UserService", "Enterprise ID not found in user info"));
        }

        LocalDate birthdate = parseBirthdate(userInfo.birthdate());
        if (birthdate == null) {
            return Mono.error(new IdentityServiceException(
                    "UserService", "Birthdate not found or invalid in user info"));
        }

        int age = Period.between(birthdate, LocalDate.now()).getYears();
        boolean isResponsibleParty = userInfo.isResponsibleParty();
        String apiIdentifier = userInfo.apiIdentifier();

        log.debug("User info extracted - eid: {}, age: {}, isRP: {}", StringSanitizer.forLog(eid), age, isResponsibleParty);

        // Age check (minimum 13)
        if (age < MINIMUM_ACCESS_AGE) {
            log.warn("User {} is under minimum age ({})", StringSanitizer.forLog(hsidUuid), age);
            return Mono.error(new AgeRestrictionException(age, MINIMUM_ACCESS_AGE));
        }

        // Fetch eligibility and managed members in parallel
        return fetchEligibilityAndManagedMembers(hsidUuid, eid, apiIdentifier, age, isResponsibleParty, birthdate);
    }

    private Mono<MemberAccess> fetchEligibilityAndManagedMembers(
            String hsidUuid,
            String eid,
            String apiIdentifier,
            int age,
            boolean isResponsibleParty,
            LocalDate birthdate) {

        // Always check eligibility for users >= 13
        Mono<EligibilityResult> eligibilityMono = eligibilityService
                .checkEligibility(eid, apiIdentifier);

        // Only check managed members if adult (>= 18) AND responsible party
        Mono<List<ManagedMember>> managedMembersMono;
        if (age >= ADULT_AGE && isResponsibleParty) {
            log.debug("User is adult RP, fetching managed members");
            managedMembersMono = managedMembersService.getManagedMembers(eid);
        } else {
            log.debug("User is not adult RP, skipping managed members fetch");
            managedMembersMono = Mono.just(List.of());
        }

        // Execute in parallel and combine results
        return Mono.zip(eligibilityMono, managedMembersMono)
                .flatMap(tuple -> {
                    EligibilityResult eligibility = tuple.getT1();
                    List<ManagedMember> managedMembers = tuple.getT2();

                    MemberAccess access = MemberAccess.create(
                            hsidUuid,
                            eid,
                            birthdate,
                            isResponsibleParty,
                            apiIdentifier,
                            eligibility,
                            managedMembers
                    );

                    // Validate access
                    if (!access.canAccessSystem()) {
                        log.warn("User {} has no access: eligibility={}, managedMembers={}",
                                StringSanitizer.forLog(hsidUuid), eligibility.status(), managedMembers.size());
                        return Mono.error(new NoAccessException(hsidUuid,
                                "No eligibility and no managed members"));
                    }

                    return Mono.just(access);
                });
    }

    private LocalDate parseBirthdate(String birthdate) {
        if (birthdate == null || birthdate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(birthdate);
        } catch (DateTimeParseException e) {
            // Do not log actual birthdate (PII) - only log the error type
            log.warn("Failed to parse birthdate: invalid format");
            return null;
        }
    }
}
