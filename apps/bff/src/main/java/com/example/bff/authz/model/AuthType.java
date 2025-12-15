package com.example.bff.authz.model;

/**
 * Authentication type determining which authorization model to use.
 */
public enum AuthType {
    /**
     * HSID authentication - uses ReBAC with permission tuples.
     * Parent users with per-dependent permissions (DAA, RPR, ROI).
     */
    HSID,

    /**
     * Proxy authentication - uses RBAC based on persona.
     * Agents, case workers, config users with role-based access.
     */
    PROXY
}
