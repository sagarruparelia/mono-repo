package com.example.bff.security.context;

public enum Persona {
    SELF,               // HSID users with active eligibility
    DELEGATE,           // HSID users 18+ with valid delegate relationships (DAA+ROI)
    AGENT,              // MSID authentication only
    CASE_WORKER,        // OHID authentication only
    CONFIG_SPECIALIST   // MSID authentication only
}
