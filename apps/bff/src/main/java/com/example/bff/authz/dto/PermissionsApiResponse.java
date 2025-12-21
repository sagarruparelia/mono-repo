package com.example.bff.authz.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * Response from the delegate-graph API.
 *
 * <p>The API returns a flat list of permissions, where each permission has:
 * <ul>
 *   <li>eid - enterprise ID of the dependent</li>
 *   <li>delegateType - DAA, RPR, or ROI</li>
 *   <li>startDate - when the permission becomes valid (yyyy-MM-dd, CST)</li>
 *   <li>stopDate - when the permission expires (yyyy-MM-dd, CST, null = no end)</li>
 *   <li>active - whether the permission is explicitly active</li>
 * </ul>
 *
 * <p>Example response:
 * <pre>{@code
 * [
 *   {"eid": "dep-1", "delegateType": "DAA", "startDate": "2024-01-15", "stopDate": "2025-12-31", "active": true},
 *   {"eid": "dep-1", "delegateType": "RPR", "startDate": "2024-01-15", "stopDate": null, "active": true},
 *   {"eid": "dep-2", "delegateType": "DAA", "startDate": "2024-03-01", "stopDate": null, "active": false}
 * ]
 * }</pre>
 */
public record PermissionsApiResponse(
        @JsonProperty("permissions") List<DelegatePermissionEntry> permissions
) {
    /**
     * A single permission entry from the delegate-graph API.
     *
     * @param enterpriseId Enterprise ID of the dependent (JSON field: "eid")
     * @param delegateType The permission type (DAA, RPR, ROI)
     * @param startDate    When the permission becomes valid (yyyy-MM-dd, CST)
     * @param stopDate     When the permission expires (null = no end date)
     * @param active       Whether the permission is explicitly active
     */
    public record DelegatePermissionEntry(
            @JsonProperty("eid") String enterpriseId,
            @JsonProperty("delegateType") String delegateType,
            @JsonProperty("startDate") @Nullable LocalDate startDate,
            @JsonProperty("stopDate") @Nullable LocalDate stopDate,
            @JsonProperty("active") boolean active
    ) {}

    /**
     * Create an empty response (no permissions).
     */
    public static PermissionsApiResponse empty() {
        return new PermissionsApiResponse(List.of());
    }
}
