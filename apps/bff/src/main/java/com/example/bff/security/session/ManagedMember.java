package com.example.bff.security.session;

import com.example.bff.security.context.DelegateType;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Set;

public record ManagedMember(
        String enterpriseId,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Set<DelegateType> delegateTypes
) implements Serializable {}
