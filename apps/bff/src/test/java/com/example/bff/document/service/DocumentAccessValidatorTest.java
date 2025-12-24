package com.example.bff.document.service;

import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.Persona;
import com.example.bff.security.exception.AuthorizationException;
import com.example.bff.security.exception.SecurityIncidentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Set;

import static com.example.bff.util.AuthContextTestBuilder.anAuthContext;

@DisplayName("DocumentAccessValidator")
class DocumentAccessValidatorTest {

    private DocumentAccessValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DocumentAccessValidator();
    }

    @Nested
    @DisplayName("SELF persona")
    class SelfPersona {

        @Test
        @DisplayName("should allow access when enterpriseId matches")
        void shouldAllowAccessWhenEnterpriseIdMatches() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.SELF)
                    .withEnterpriseId("ENT-001")
                    .build();

            StepVerifier.create(validator.validateAccess(ctx, "ENT-001"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw SecurityIncidentException when accessing another member's document")
        void shouldThrowWhenAccessingOtherMembersDocument() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.SELF)
                    .withEnterpriseId("ENT-001")
                    .withLoggedInMemberIdValue("self-user-123")
                    .build();

            StepVerifier.create(validator.validateAccess(ctx, "ENT-999"))
                    .expectErrorSatisfies(error -> {
                        org.assertj.core.api.Assertions.assertThat(error)
                                .isInstanceOf(SecurityIncidentException.class);
                        SecurityIncidentException incident = (SecurityIncidentException) error;
                        org.assertj.core.api.Assertions.assertThat(incident.getLoggedInMemberIdValue())
                                .isEqualTo("self-user-123");
                        org.assertj.core.api.Assertions.assertThat(incident.getAttemptedEnterpriseId())
                                .isEqualTo("ENT-999");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("DELEGATE persona")
    class DelegatePersona {

        @Test
        @DisplayName("should allow access with all required delegate types")
        void shouldAllowAccessWithAllRequiredTypes() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withEnterpriseId("MANAGED-ENT-001")
                    .withActiveDelegateTypes(Set.of(DelegateType.ROI, DelegateType.DAA, DelegateType.RPR))
                    .build();

            StepVerifier.create(validator.validateAccess(ctx, "MANAGED-ENT-001"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw AuthorizationException when missing ROI")
        void shouldThrowWhenMissingRoi() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withEnterpriseId("MANAGED-ENT-001")
                    .withActiveDelegateTypes(Set.of(DelegateType.DAA, DelegateType.RPR))
                    .withLoggedInMemberIdValue("delegate-user-123")
                    .build();

            StepVerifier.create(validator.validateAccess(ctx, "MANAGED-ENT-001"))
                    .expectErrorSatisfies(error -> {
                        org.assertj.core.api.Assertions.assertThat(error)
                                .isInstanceOf(AuthorizationException.class);
                        org.assertj.core.api.Assertions.assertThat(error.getMessage())
                                .contains("ROI, DAA, and RPR");
                    })
                    .verify();
        }

        @Test
        @DisplayName("should throw AuthorizationException when missing DAA")
        void shouldThrowWhenMissingDaa() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withEnterpriseId("MANAGED-ENT-001")
                    .withActiveDelegateTypes(Set.of(DelegateType.ROI, DelegateType.RPR))
                    .build();

            StepVerifier.create(validator.validateAccess(ctx, "MANAGED-ENT-001"))
                    .expectError(AuthorizationException.class)
                    .verify();
        }

        @Test
        @DisplayName("should throw AuthorizationException when accessing unauthorized enterpriseId")
        void shouldThrowWhenAccessingUnauthorizedEnterpriseId() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withEnterpriseId("MANAGED-ENT-001")
                    .withActiveDelegateTypes(Set.of(DelegateType.ROI, DelegateType.DAA, DelegateType.RPR))
                    .withLoggedInMemberIdValue("delegate-user-123")
                    .build();

            StepVerifier.create(validator.validateAccess(ctx, "UNAUTHORIZED-ENT-999"))
                    .expectError(AuthorizationException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("AGENT persona")
    class AgentPersona {

        @Test
        @DisplayName("should allow access to any enterpriseId")
        void shouldAllowAccessToAnyEnterpriseId() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.AGENT)
                    .withEnterpriseId("ANY-ENT-001")
                    .build();

            StepVerifier.create(validator.validateAccess(ctx, "ANY-ENT-001"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("CASE_WORKER persona")
    class CaseWorkerPersona {

        @Test
        @DisplayName("should allow access to any enterpriseId")
        void shouldAllowAccessToAnyEnterpriseId() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.CASE_WORKER)
                    .withEnterpriseId("ANY-ENT-002")
                    .build();

            StepVerifier.create(validator.validateAccess(ctx, "ANY-ENT-002"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("CONFIG_SPECIALIST persona")
    class ConfigSpecialistPersona {

        @Test
        @DisplayName("should deny all access")
        void shouldDenyAllAccess() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.CONFIG_SPECIALIST)
                    .withEnterpriseId("ANY-ENT-003")
                    .withLoggedInMemberIdValue("config-user-123")
                    .build();

            StepVerifier.create(validator.validateAccess(ctx, "ANY-ENT-003"))
                    .expectErrorSatisfies(error -> {
                        org.assertj.core.api.Assertions.assertThat(error)
                                .isInstanceOf(AuthorizationException.class);
                        org.assertj.core.api.Assertions.assertThat(error.getMessage())
                                .contains("CONFIG_SPECIALIST");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("validateUpload")
    class ValidateUpload {

        @Test
        @DisplayName("should delegate to validateAccess")
        void shouldDelegateToValidateAccess() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.SELF)
                    .withEnterpriseId("ENT-001")
                    .build();

            StepVerifier.create(validator.validateUpload(ctx, "ENT-001"))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should fail upload validation for CONFIG_SPECIALIST")
        void shouldFailUploadForConfigSpecialist() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.CONFIG_SPECIALIST)
                    .withEnterpriseId("ENT-001")
                    .build();

            StepVerifier.create(validator.validateUpload(ctx, "ENT-001"))
                    .expectError(AuthorizationException.class)
                    .verify();
        }
    }
}
