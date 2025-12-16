package com.example.bff.health.controller;

import com.example.bff.health.dto.AllergyDto;
import com.example.bff.health.dto.ImmunizationDto;
import com.example.bff.health.dto.MedicationDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Controller for health-related MFE endpoints
 * Provides immunization, allergy, and medication data for members
 */
@RestController
@RequestMapping("/api/mfe")
public class HealthController {

    /**
     * Get immunization records for a member
     */
    @GetMapping("/immunizations/{memberId}")
    public Mono<ResponseEntity<List<ImmunizationDto>>> getImmunizations(
            @PathVariable String memberId) {
        // TODO: Fetch from external health service
        // For now, return mock data
        List<ImmunizationDto> immunizations = List.of(
                new ImmunizationDto(
                        "imm-001",
                        "COVID-19 (Pfizer-BioNTech)",
                        "2023-09-15",
                        "ABC Pharmacy",
                        "EL3246"
                ),
                new ImmunizationDto(
                        "imm-002",
                        "Influenza (Flu Shot)",
                        "2023-10-01",
                        "City Health Clinic",
                        "FL2023-789"
                ),
                new ImmunizationDto(
                        "imm-003",
                        "Tdap (Tetanus, Diphtheria, Pertussis)",
                        "2022-05-20",
                        "Family Medical Center",
                        "TD2022-456"
                )
        );

        return Mono.just(ResponseEntity.ok(immunizations));
    }

    /**
     * Get allergy records for a member
     */
    @GetMapping("/allergies/{memberId}")
    public Mono<ResponseEntity<List<AllergyDto>>> getAllergies(
            @PathVariable String memberId) {
        // TODO: Fetch from external health service
        // For now, return mock data
        List<AllergyDto> allergies = List.of(
                new AllergyDto(
                        "allergy-001",
                        "Penicillin",
                        "Hives and skin rash",
                        "moderate",
                        "2015-03-10"
                ),
                new AllergyDto(
                        "allergy-002",
                        "Peanuts",
                        "Anaphylaxis",
                        "severe",
                        "2010-08-22"
                )
        );

        return Mono.just(ResponseEntity.ok(allergies));
    }

    /**
     * Get medication records for a member
     */
    @GetMapping("/medications/{memberId}")
    public Mono<ResponseEntity<List<MedicationDto>>> getMedications(
            @PathVariable String memberId) {
        // TODO: Fetch from external health service
        // For now, return mock data
        List<MedicationDto> medications = List.of(
                new MedicationDto(
                        "med-001",
                        "Lisinopril",
                        "10mg",
                        "Once daily",
                        "2023-01-15",
                        "Dr. Sarah Smith"
                ),
                new MedicationDto(
                        "med-002",
                        "Metformin",
                        "500mg",
                        "Twice daily with meals",
                        "2022-11-30",
                        "Dr. Michael Johnson"
                ),
                new MedicationDto(
                        "med-003",
                        "Vitamin D3",
                        "2000 IU",
                        "Once daily",
                        "2023-06-01",
                        "Dr. Sarah Smith"
                )
        );

        return Mono.just(ResponseEntity.ok(medications));
    }
}
