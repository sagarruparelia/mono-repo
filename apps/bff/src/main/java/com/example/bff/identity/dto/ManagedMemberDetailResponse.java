package com.example.bff.identity.dto;

import com.example.bff.auth.model.DelegatePermission;
import com.example.bff.auth.model.DelegateType;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for managed member details with permission information.
 *
 * <p>Used by the AccessController to return detailed information about
 * members the logged-in user can act on behalf of, including permission
 * validity dates.</p>
 *
 * @param enterpriseId Enterprise ID of the managed member
 * @param firstName    First name of the managed member
 * @param lastName     Last name of the managed member
 * @param birthDate    Birth date of the managed member
 * @param permissions  List of delegate permissions with validity dates
 */
public record ManagedMemberDetailResponse(
        @NonNull String enterpriseId,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable LocalDate birthDate,
        @NonNull List<PermissionEntry> permissions
) {
    /**
     * A single permission entry with delegate type and validity dates.
     *
     * @param delegateType The type of delegate permission (DAA, RPR, ROI)
     * @param startDate    Date from which the permission is valid (inclusive)
     * @param endDate      Date until which the permission is valid (inclusive, null = no end date)
     * @param active       Whether the permission is currently active
     */
    public record PermissionEntry(
            @NonNull DelegateType delegateType,
            @Nullable LocalDate startDate,
            @Nullable LocalDate endDate,
            boolean active
    ) {
        /**
         * Create a PermissionEntry from a DelegateType and DelegatePermission.
         */
        public static PermissionEntry from(@NonNull DelegateType type, @NonNull DelegatePermission permission) {
            return new PermissionEntry(
                    type,
                    permission.startDate(),
                    permission.stopDate(),
                    permission.active()
            );
        }
    }

    /**
     * Create a response from identity and permission data.
     *
     * @param enterpriseId Enterprise ID of the managed member
     * @param firstName    First name of the managed member
     * @param lastName     Last name of the managed member
     * @param birthDate    Birth date of the managed member
     * @param permissions  Map of delegate types to their permissions
     * @return ManagedMemberDetailResponse with all permission entries
     */
    public static ManagedMemberDetailResponse from(
            @NonNull String enterpriseId,
            @Nullable String firstName,
            @Nullable String lastName,
            @Nullable LocalDate birthDate,
            @Nullable Map<DelegateType, DelegatePermission> permissions) {

        List<PermissionEntry> entries = permissions == null ? List.of() :
                permissions.entrySet().stream()
                        .map(e -> PermissionEntry.from(e.getKey(), e.getValue()))
                        .toList();

        return new ManagedMemberDetailResponse(enterpriseId, firstName, lastName, birthDate, entries);
    }
}
