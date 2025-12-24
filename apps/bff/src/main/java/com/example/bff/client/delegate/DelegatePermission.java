package com.example.bff.client.delegate;

import com.example.bff.security.context.DelegateType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class DelegatePermission {

    private DelegateType delegateType;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String enterpriseId;
    private LocalDate startDate;
    private LocalDate stopDate;
    private boolean active;

    // Active and within date range
    public boolean isCurrentlyValid() {
        if (!active) {
            return false;
        }

        LocalDate today = LocalDate.now();

        // Check start date
        if (startDate != null && today.isBefore(startDate)) {
            return false;
        }

        // Check stop date
        if (stopDate != null && today.isAfter(stopDate)) {
            return false;
        }

        return true;
    }
}
