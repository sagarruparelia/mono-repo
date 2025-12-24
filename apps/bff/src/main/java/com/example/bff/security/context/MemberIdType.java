package com.example.bff.security.context;

// IDP type determines allowed personas: HSID->SELF/DELEGATE, MSID->AGENT/CONFIG_SPECIALIST, OHID->CASE_WORKER
public enum MemberIdType {
    HSID,  // HealthSafe ID - browser authentication
    MSID,  // Member Service ID - external partners
    OHID   // Other Health ID - external partners
}
