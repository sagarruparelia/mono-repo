package com.example.bff.authz.abac.model;

/**
 * ABAC Actions - represents what the subject wants to do with the resource.
 */
public enum Action {
    /**
     * View basic resource information.
     * HSID: Requires DAA + RPR
     * Proxy: Requires assigned member or config persona
     */
    VIEW,

    /**
     * View sensitive resource information (medical, financial, etc.).
     * HSID: Requires DAA + RPR + ROI
     * Proxy: Requires config persona only
     */
    VIEW_SENSITIVE,

    /**
     * Edit resource information.
     * HSID: Requires DAA + RPR (no edit for parents currently)
     * Proxy: Requires config persona
     */
    EDIT,

    /**
     * Delete resource.
     * HSID: Not allowed
     * Proxy: Requires config persona
     */
    DELETE,

    /**
     * List resources (e.g., list dependents).
     * HSID: Returns only viewable dependents
     * Proxy: Returns assigned members or all for config
     */
    LIST,

    /**
     * Upload a resource (e.g., upload document).
     * HSID: Youth can upload own docs, parent needs DAA + RPR for dependent
     * Proxy: Requires assigned member or config persona
     */
    UPLOAD
}
