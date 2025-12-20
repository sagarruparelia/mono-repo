package com.example.bff.identity.service;

import com.example.bff.identity.dto.UserInfoResponse;
import com.example.bff.identity.exception.AgeRestrictionException;
import com.example.bff.identity.exception.IdentityServiceException;
import com.example.bff.identity.exception.NoAccessException;
import com.example.bff.identity.model.EligibilityResult;
import com.example.bff.identity.model.ManagedMember;
import com.example.bff.identity.model.MemberAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * Flow:
 * 1. Get user info from User Service API
 * 2. Extract EID, birthdate, memberType (PR)
 * 3. Check age (reject if < 13)
 * 4. Parallel calls:
 *    - Eligibility API (all users >= 13)
 *    - Permission API (only if age >= 18 && memberType = PR)
 * 5. Validate access (must have eligibility OR managed members)
 * 6. Return consolidated MemberAccess
 */
@Service
public class MemberAccessOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(MemberAccessOrchestrator.class);

    private final UserInfoService userInfoService;
    private final EligibilityService eligibilityService;
    private final ManagedMemberService managedMemberService;

    public MemberAccessOrchestrator(
            UserInfoService userInfoService,
            EligibilityService eligibilityService,
            ManagedMemberService managedMemberService) {
        this.userInfoService = userInfoService;
        this.eligibilityService = eligibilityService;
        this.managedMemberService = managedMemberService;
    }

    /**
     * Resolve member access for a user after HSID login.
     *
     * @param hsidUuid User's HSID UUID (from token sub claim)
     * @return MemberAccess with all resolved information
     * @throws AgeRestrictionException if user is under 13
     * @throws NoAccessException       if user has no eligibility and no managed members
     * @throws IdentityServiceException if User Service API fails
     */
    @NonNull
    public Mono<MemberAccess> resolveMemberAccess(@NonNull String hsidUuid) {
        LOG.info("Resolving member access for hsidUuid: {}", hsidUuid);

        return userInfoService.getUserInfo(hsidUuid)
                .flatMap(userInfo -> processUserInfo(hsidUuid, userInfo))
                .doOnSuccess(access -> LOG.info(
                        "Member access resolved for hsidUuid: {}, persona: {}, canAccess: {}",
                        hsidUuid, access.getEffectivePersona(), access.canAccessSystem()))
                .doOnError(e -> LOG.error(
                        "Failed to resolve member access for hsidUuid {}: {}", hsidUuid, e.getMessage()));
    }

    /**
     * Process user info and fetch eligibility/permissions.
     */
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

        LOG.debug("User info extracted - eid: {}, age: {}, isRP: {}", eid, age, isResponsibleParty);

        // Age check (minimum 13)
        if (age < MINIMUM_ACCESS_AGE) {
            LOG.warn("User {} is under minimum age ({})", hsidUuid, age);
            return Mono.error(new AgeRestrictionException(age, MINIMUM_ACCESS_AGE));
        }

        // Fetch eligibility and managed members in parallel
        return fetchEligibilityAndPermissions(hsidUuid, eid, apiIdentifier, age, isResponsibleParty, birthdate);
    }

    /**
     * Fetch eligibility and permissions (if applicable) in parallel.
     */
    private Mono<MemberAccess> fetchEligibilityAndPermissions(
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
            LOG.debug("User is adult RP, fetching managed members");
            managedMembersMono = managedMemberService.getManagedMembers(eid, apiIdentifier);
        } else {
            LOG.debug("User is not adult RP, skipping managed members fetch");
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
                        LOG.warn("User {} has no access: eligibility={}, managedMembers={}",
                                hsidUuid, eligibility.status(), managedMembers.size());
                        return Mono.error(new NoAccessException(hsidUuid,
                                "No eligibility and no managed members"));
                    }

                    return Mono.just(access);
                });
    }

    /**
     * Parse birthdate string to LocalDate.
     * Expects ISO format (yyyy-MM-dd).
     */
    private LocalDate parseBirthdate(String birthdate) {
        if (birthdate == null || birthdate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(birthdate);
        } catch (DateTimeParseException e) {
            // Do not log actual birthdate (PII) - only log the error type
            LOG.warn("Failed to parse birthdate: invalid format");
            return null;
        }
    }
}
