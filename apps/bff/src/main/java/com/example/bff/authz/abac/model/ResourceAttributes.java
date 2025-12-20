package com.example.bff.authz.abac.model;

/**
 * ABAC Resource Attributes - represents the resource being accessed.
 */
public record ResourceAttributes(
        ResourceType type,
        String id,
        Sensitivity sensitivity,
        String ownerId,
        String partnerId
) {
    /**
     * Resource types in the system.
     */
    public enum ResourceType {
        DEPENDENT,       // Child/dependent (HSID context)
        MEMBER,          // Member (Proxy context)
        PROFILE,
        MEDICAL_RECORD,
        DOCUMENT,
        HEALTH_DATA      // Immunizations, allergies, conditions from ECDH
    }

    /**
     * Sensitivity level of the resource.
     */
    public enum Sensitivity {
        NORMAL,
        SENSITIVE  // Requires ROI (HSID) or config persona (Proxy)
    }

    /**
     * Create resource attributes for a dependent.
     */
    public static ResourceAttributes dependent(String dependentId) {
        return new ResourceAttributes(
                ResourceType.DEPENDENT,
                dependentId,
                Sensitivity.NORMAL,
                null,
                null
        );
    }

    /**
     * Create resource attributes for a dependent with sensitivity.
     */
    public static ResourceAttributes dependent(String dependentId, Sensitivity sensitivity) {
        return new ResourceAttributes(
                ResourceType.DEPENDENT,
                dependentId,
                sensitivity,
                null,
                null
        );
    }

    /**
     * Create resource attributes for a member.
     */
    public static ResourceAttributes member(String memberId) {
        return new ResourceAttributes(
                ResourceType.MEMBER,
                memberId,
                Sensitivity.NORMAL,
                null,
                null
        );
    }

    /**
     * Create resource attributes for a member with sensitivity.
     */
    public static ResourceAttributes member(String memberId, Sensitivity sensitivity) {
        return new ResourceAttributes(
                ResourceType.MEMBER,
                memberId,
                sensitivity,
                null,
                null
        );
    }

    /**
     * Create resource attributes for a member with partner context.
     */
    public static ResourceAttributes member(String memberId, String partnerId, Sensitivity sensitivity) {
        return new ResourceAttributes(
                ResourceType.MEMBER,
                memberId,
                sensitivity,
                null,
                partnerId
        );
    }

    /**
     * Check if resource is sensitive.
     */
    public boolean isSensitive() {
        return sensitivity == Sensitivity.SENSITIVE;
    }

    /**
     * Check if resource is a dependent type.
     */
    public boolean isDependent() {
        return type == ResourceType.DEPENDENT;
    }

    /**
     * Check if resource is a member type.
     */
    public boolean isMember() {
        return type == ResourceType.MEMBER;
    }

    /**
     * Check if resource is a document type.
     */
    public boolean isDocument() {
        return type == ResourceType.DOCUMENT;
    }

    /**
     * Create resource attributes for a document.
     * Documents are always sensitive (require DAA+RPR+ROI for parent view).
     *
     * @param documentId the document ID (or "list" for list operations, "new" for upload)
     * @param ownerId the youth (member) who owns the document
     */
    public static ResourceAttributes document(String documentId, String ownerId) {
        return new ResourceAttributes(
                ResourceType.DOCUMENT,
                documentId,
                Sensitivity.SENSITIVE,
                ownerId,
                null
        );
    }

    /**
     * Create resource attributes for health data.
     * Health data (immunizations, allergies, conditions) is sensitive.
     *
     * @param memberId the member whose health data is being accessed
     */
    public static ResourceAttributes healthData(String memberId) {
        return new ResourceAttributes(
                ResourceType.HEALTH_DATA,
                memberId,
                Sensitivity.SENSITIVE,
                memberId,
                null
        );
    }

    /**
     * Create resource attributes for health data with partner context.
     *
     * @param memberId the member whose health data is being accessed
     * @param partnerId the partner organization ID (for proxy context)
     */
    public static ResourceAttributes healthData(String memberId, String partnerId) {
        return new ResourceAttributes(
                ResourceType.HEALTH_DATA,
                memberId,
                Sensitivity.SENSITIVE,
                memberId,
                partnerId
        );
    }

    /**
     * Check if resource is health data type.
     */
    public boolean isHealthData() {
        return type == ResourceType.HEALTH_DATA;
    }
}
