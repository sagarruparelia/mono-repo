package com.example.bff.auth.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Represents a single delegate permission with temporal validity.
 *
 * <p>Each permission (DAA, RPR, ROI) has its own start date, stop date, and active status.
 * A permission is considered valid only if:
 * <ul>
 *   <li>The {@code active} flag is {@code true}</li>
 *   <li>The current date is on or after {@code startDate}</li>
 *   <li>The current date is on or before {@code stopDate} (if set)</li>
 * </ul>
 *
 * <p>All dates use CST (America/Chicago) timezone for consistency.
 *
 * @param startDate The date from which the permission is valid (inclusive)
 * @param stopDate  The date until which the permission is valid (inclusive, null = no end date)
 * @param active    Whether the permission is explicitly active (false = deactivated)
 */
public record DelegatePermission(
        @JsonProperty("startDate") @Nullable LocalDate startDate,
        @JsonProperty("stopDate") @Nullable LocalDate stopDate,
        @JsonProperty("active") boolean active
) {
    /**
     * CST timezone used for all date comparisons.
     */
    private static final ZoneId CST_ZONE = ZoneId.of("America/Chicago");

    /**
     * Check if this permission is currently valid.
     *
     * <p>A permission is valid if:
     * <ol>
     *   <li>The {@code active} flag is {@code true}</li>
     *   <li>Current date >= startDate (or startDate is null)</li>
     *   <li>Current date <= stopDate (or stopDate is null)</li>
     * </ol>
     *
     * @return {@code true} if the permission is currently valid
     */
    @JsonIgnore
    public boolean isCurrentlyValid() {
        if (!active) {
            return false;
        }

        LocalDate today = LocalDate.now(CST_ZONE);

        // Check if we're past the start date (or no start date specified)
        boolean afterStart = startDate == null || !today.isBefore(startDate);

        // Check if we're before the stop date (or no stop date specified)
        boolean beforeStop = stopDate == null || !today.isAfter(stopDate);

        return afterStart && beforeStop;
    }

    /**
     * Check if this permission has expired (stop date has passed).
     *
     * @return {@code true} if stopDate is set and current date is after stopDate
     */
    @JsonIgnore
    public boolean isExpired() {
        if (stopDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now(CST_ZONE);
        return today.isAfter(stopDate);
    }

    /**
     * Check if this permission has not yet started.
     *
     * @return {@code true} if startDate is set and current date is before startDate
     */
    @JsonIgnore
    public boolean isNotYetActive() {
        if (startDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now(CST_ZONE);
        return today.isBefore(startDate);
    }

    /**
     * Create a permission that is always valid (for testing or default cases).
     */
    public static DelegatePermission alwaysValid() {
        return new DelegatePermission(null, null, true);
    }

    /**
     * Create an inactive permission.
     */
    public static DelegatePermission inactive() {
        return new DelegatePermission(null, null, false);
    }
}
