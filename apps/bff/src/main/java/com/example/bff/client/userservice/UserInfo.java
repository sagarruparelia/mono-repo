package com.example.bff.client.userservice;

import java.time.LocalDate;

public record UserInfo(
        String firstName,
        String lastName,
        LocalDate birthDate,
        String enterpriseId,
        String apiIdentifier,
        String memberType,
        String email,
        String phone
) {}
