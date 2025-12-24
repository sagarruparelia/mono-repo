package com.example.bff.security.context;

public enum DelegateType {
    RPR,  // Representative - basic delegate permission (required with DAA)
    DAA,  // Data Access Agreement - allows access to member data (required with RPR)
    ROI   // Release of Information - allows access to sensitive/medical information (optional)
}
