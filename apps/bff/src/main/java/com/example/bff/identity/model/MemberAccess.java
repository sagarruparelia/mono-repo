package com.example.bff.identity.model;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Consolidated member access information after identity enrichment.
 * Determines what the logged-in user can access in the system.
 */
public record MemberAccess(
        @NonNull String hsidUuid,
        @NonNull String enterpriseId,
        @NonNull LocalDate birthdate,
        int age,
        boolean isResponsibleParty,
        @Nullable String apiIdentifier,
        @NonNull EligibilityStatus eligibilityStatus,
        @Nullable LocalDate termDate,
        @NonNull List<ManagedMember> managedMembers
) {
    /**
     * Minimum age to access the system.
     */
    public static final int MINIMUM_ACCESS_AGE = 13;

    /**
     * Adult age threshold for Responsible Party checks.
     */
    public static final int ADULT_AGE = 18;

    /**
     * Create a MemberAccess with calculated age.
     */
    public static MemberAccess create(
            @NonNull String hsidUuid,
            @NonNull String enterpriseId,
            @NonNull LocalDate birthdate,
            boolean isResponsibleParty,
            @Nullable String apiIdentifier,
            @NonNull EligibilityResult eligibilityResult,
            @NonNull List<ManagedMember> managedMembers) {

        int age = Period.between(birthdate, LocalDate.now()).getYears();

        return new MemberAccess(
                hsidUuid,
                enterpriseId,
                birthdate,
                age,
                isResponsibleParty,
                apiIdentifier,
                eligibilityResult.status(),
                eligibilityResult.termDate(),
                managedMembers
        );
    }

    /**
     * Check if user meets minimum age requirement.
     *
     * @return true if age >= 13
     */
    public boolean meetsMinimumAge() {
        return age >= MINIMUM_ACCESS_AGE;
    }

    /**
     * Check if user is an adult (eligible for RP checks).
     *
     * @return true if age >= 18
     */
    public boolean isAdult() {
        return age >= ADULT_AGE;
    }

    /**
     * Check if user has any eligibility-based self-access.
     *
     * @return true if eligibility is ACTIVE or INACTIVE
     */
    public boolean hasEligibility() {
        return eligibilityStatus.hasSelfAccess();
    }

    /**
     * Check if user has any active managed members.
     *
     * @return true if managedMembers is not empty
     */
    public boolean hasActiveManagedMembers() {
        return managedMembers != null && !managedMembers.isEmpty();
    }

    /**
     * Check if user can access the system.
     * User can access if:
     * - Age >= 13 AND
     * - (Has eligibility OR has active managed members)
     *
     * @return true if user can access the system
     */
    public boolean canAccessSystem() {
        if (!meetsMinimumAge()) {
            return false;
        }
        return hasEligibility() || hasActiveManagedMembers();
    }

    /**
     * Get the effective persona for this user.
     * - If user has eligibility: "individual" (self-access)
     * - If user has managed members only: "parent" (RP access)
     *
     * Note: MVP doesn't support both - we prioritize eligibility.
     *
     * @return Effective persona string
     */
    @NonNull
    public String getEffectivePersona() {
        if (hasEligibility()) {
            return "individual";
        }
        if (hasActiveManagedMembers()) {
            return "parent";
        }
        return "none";
    }

    /**
     * Get the earliest permission end date among managed members.
     * Used for session expiration tracking.
     *
     * @return Earliest end date or null if no managed members
     */
    @Nullable
    public LocalDate getEarliestPermissionEndDate() {
        if (managedMembers == null || managedMembers.isEmpty()) {
            return null;
        }
        return managedMembers.stream()
                .map(ManagedMember::permissionEndDate)
                .min(LocalDate::compareTo)
                .orElse(null);
    }
}
