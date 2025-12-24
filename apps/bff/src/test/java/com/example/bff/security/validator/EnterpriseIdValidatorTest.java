package com.example.bff.security.validator;

import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.MemberIdType;
import com.example.bff.security.context.Persona;
import com.example.bff.security.exception.AuthorizationException;
import com.example.bff.security.exception.SecurityIncidentException;
import com.example.bff.security.session.ManagedMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.Set;

import static com.example.bff.util.AuthContextTestBuilder.ManagedMemberTestBuilder.aManagedMember;
import static com.example.bff.util.AuthContextTestBuilder.anAuthContext;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EnterpriseIdValidator")
class EnterpriseIdValidatorTest {

    private EnterpriseIdValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EnterpriseIdValidator();
    }

    @Nested
    @DisplayName("SELF persona")
    class SelfPersona {

        @Test
        @DisplayName("should use own enterpriseId without param")
        void shouldUseOwnEnterpriseId() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.SELF)
                    .withEnterpriseId("SELF-ENT-001")
                    .build();

            StepVerifier.create(validator.resolveEnterpriseId(ctx, null, new DelegateType[0]))
                    .assertNext(result -> {
                        assertThat(result.enterpriseId()).isEqualTo("SELF-ENT-001");
                        assertThat(result.persona()).isEqualTo(Persona.SELF);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should ignore enterpriseId param if provided")
        void shouldIgnoreEnterpriseIdParam() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.SELF)
                    .withEnterpriseId("SELF-ENT-001")
                    .build();

            // Even if a param is passed, SELF should use their own enterpriseId
            StepVerifier.create(validator.resolveEnterpriseId(ctx, "OTHER-ENT-999", new DelegateType[0]))
                    .assertNext(result -> {
                        assertThat(result.enterpriseId()).isEqualTo("SELF-ENT-001");
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("DELEGATE persona")
    class DelegatePersona {

        private ManagedMember managedMember;
        private AuthContext delegateCtx;

        @BeforeEach
        void setUp() {
            managedMember = aManagedMember()
                    .withEnterpriseId("MANAGED-ENT-001")
                    .withFirstName("Managed")
                    .withLastName("Member")
                    .withDelegateTypes(Set.of(DelegateType.RPR, DelegateType.DAA))
                    .build();

            delegateCtx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withEnterpriseId("DELEGATE-OWN-ENT")
                    .withLoggedInMemberIdValue("delegate-hsid-uuid")
                    .withDelegateTypes(Set.of(DelegateType.RPR, DelegateType.DAA))
                    .withManagedMember(managedMember)
                    .build();
        }

        @Test
        @DisplayName("should resolve valid managed member enterpriseId")
        void shouldResolveValidManagedMember() {
            StepVerifier.create(validator.resolveEnterpriseId(
                            delegateCtx, "MANAGED-ENT-001", new DelegateType[0]))
                    .assertNext(result -> {
                        assertThat(result.enterpriseId()).isEqualTo("MANAGED-ENT-001");
                        assertThat(result.activeDelegateTypes())
                                .containsExactlyInAnyOrder(DelegateType.RPR, DelegateType.DAA);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw AuthorizationException when enterpriseId is missing")
        void shouldThrowWhenEnterpriseIdMissing() {
            StepVerifier.create(validator.resolveEnterpriseId(delegateCtx, null, new DelegateType[0]))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthorizationException.class);
                        assertThat(error.getMessage()).contains("enterpriseId is required");
                    })
                    .verify();
        }

        @Test
        @DisplayName("should throw AuthorizationException when enterpriseId is blank")
        void shouldThrowWhenEnterpriseIdBlank() {
            StepVerifier.create(validator.resolveEnterpriseId(delegateCtx, "   ", new DelegateType[0]))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthorizationException.class);
                        assertThat(error.getMessage()).contains("enterpriseId is required");
                    })
                    .verify();
        }

        @Test
        @DisplayName("should throw SecurityIncidentException for unauthorized enterpriseId")
        void shouldThrowSecurityIncidentForUnauthorizedAccess() {
            StepVerifier.create(validator.resolveEnterpriseId(
                            delegateCtx, "UNAUTHORIZED-ENT-999", new DelegateType[0]))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(SecurityIncidentException.class);
                        SecurityIncidentException incident = (SecurityIncidentException) error;
                        assertThat(incident.getLoggedInMemberIdValue()).isEqualTo("delegate-hsid-uuid");
                        assertThat(incident.getAttemptedEnterpriseId()).isEqualTo("UNAUTHORIZED-ENT-999");
                        assertThat(incident.getIncidentType()).isEqualTo("UNAUTHORIZED_ACCESS_ATTEMPT");
                    })
                    .verify();
        }

        @Test
        @DisplayName("should validate required delegate types")
        void shouldValidateRequiredDelegateTypes() {
            DelegateType[] required = {DelegateType.RPR, DelegateType.DAA};

            StepVerifier.create(validator.resolveEnterpriseId(
                            delegateCtx, "MANAGED-ENT-001", required))
                    .assertNext(result -> {
                        assertThat(result.enterpriseId()).isEqualTo("MANAGED-ENT-001");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw AuthorizationException when missing required delegate types")
        void shouldThrowWhenMissingRequiredDelegateTypes() {
            // Create managed member with only RPR (missing DAA)
            ManagedMember limitedMember = aManagedMember()
                    .withEnterpriseId("LIMITED-ENT-001")
                    .withDelegateTypes(Set.of(DelegateType.RPR))
                    .build();

            AuthContext limitedDelegateCtx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withManagedMember(limitedMember)
                    .build();

            DelegateType[] required = {DelegateType.RPR, DelegateType.DAA};

            StepVerifier.create(validator.resolveEnterpriseId(
                            limitedDelegateCtx, "LIMITED-ENT-001", required))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthorizationException.class);
                        assertThat(error.getMessage()).contains("Missing required delegate types");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("AGENT persona")
    class AgentPersona {

        private AuthContext agentCtx;

        @BeforeEach
        void setUp() {
            agentCtx = anAuthContext()
                    .withPersona(Persona.AGENT)
                    .withLoggedInMemberIdType(MemberIdType.MSID)
                    .withLoggedInMemberIdValue("agent-msid-123")
                    .build();
        }

        @Test
        @DisplayName("should resolve enterpriseId from param")
        void shouldResolveEnterpriseIdFromParam() {
            StepVerifier.create(validator.resolveEnterpriseId(
                            agentCtx, "MEMBER-ENT-001", new DelegateType[0]))
                    .assertNext(result -> {
                        assertThat(result.enterpriseId()).isEqualTo("MEMBER-ENT-001");
                        assertThat(result.persona()).isEqualTo(Persona.AGENT);
                        assertThat(result.activeDelegateTypes()).isEmpty();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw AuthorizationException when enterpriseId is missing")
        void shouldThrowWhenEnterpriseIdMissing() {
            StepVerifier.create(validator.resolveEnterpriseId(agentCtx, null, new DelegateType[0]))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthorizationException.class);
                        assertThat(error.getMessage()).contains("enterpriseId is required for AGENT");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("CASE_WORKER persona")
    class CaseWorkerPersona {

        private AuthContext caseWorkerCtx;

        @BeforeEach
        void setUp() {
            caseWorkerCtx = anAuthContext()
                    .withPersona(Persona.CASE_WORKER)
                    .withLoggedInMemberIdType(MemberIdType.OHID)
                    .withLoggedInMemberIdValue("caseworker-ohid-123")
                    .build();
        }

        @Test
        @DisplayName("should resolve enterpriseId from param")
        void shouldResolveEnterpriseIdFromParam() {
            StepVerifier.create(validator.resolveEnterpriseId(
                            caseWorkerCtx, "MEMBER-ENT-002", new DelegateType[0]))
                    .assertNext(result -> {
                        assertThat(result.enterpriseId()).isEqualTo("MEMBER-ENT-002");
                        assertThat(result.persona()).isEqualTo(Persona.CASE_WORKER);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw AuthorizationException when enterpriseId is missing")
        void shouldThrowWhenEnterpriseIdMissing() {
            StepVerifier.create(validator.resolveEnterpriseId(caseWorkerCtx, null, new DelegateType[0]))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthorizationException.class);
                        assertThat(error.getMessage()).contains("enterpriseId is required for CASE_WORKER");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("CONFIG_SPECIALIST persona")
    class ConfigSpecialistPersona {

        private AuthContext configSpecialistCtx;

        @BeforeEach
        void setUp() {
            configSpecialistCtx = anAuthContext()
                    .withPersona(Persona.CONFIG_SPECIALIST)
                    .withLoggedInMemberIdType(MemberIdType.MSID)
                    .withLoggedInMemberIdValue("config-msid-123")
                    .build();
        }

        @Test
        @DisplayName("should resolve enterpriseId from param")
        void shouldResolveEnterpriseIdFromParam() {
            StepVerifier.create(validator.resolveEnterpriseId(
                            configSpecialistCtx, "MEMBER-ENT-003", new DelegateType[0]))
                    .assertNext(result -> {
                        assertThat(result.enterpriseId()).isEqualTo("MEMBER-ENT-003");
                        assertThat(result.persona()).isEqualTo(Persona.CONFIG_SPECIALIST);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw AuthorizationException when enterpriseId is missing")
        void shouldThrowWhenEnterpriseIdMissing() {
            StepVerifier.create(validator.resolveEnterpriseId(configSpecialistCtx, "", new DelegateType[0]))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthorizationException.class);
                        assertThat(error.getMessage()).contains("enterpriseId is required for CONFIG_SPECIALIST");
                    })
                    .verify();
        }
    }

    @Nested
    @DisplayName("validateRequiredDelegateTypes")
    class ValidateRequiredDelegateTypes {

        @Test
        @DisplayName("should pass when all required delegate types are present")
        void shouldPassWhenAllTypesPresent() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withActiveDelegateTypes(Set.of(DelegateType.RPR, DelegateType.DAA, DelegateType.ROI))
                    .build();

            DelegateType[] required = {DelegateType.RPR, DelegateType.DAA};

            StepVerifier.create(validator.validateRequiredDelegateTypes(ctx, required))
                    .assertNext(result -> assertThat(result).isEqualTo(ctx))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should pass when no required delegate types specified")
        void shouldPassWhenNoRequiredTypes() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withActiveDelegateTypes(Set.of(DelegateType.RPR))
                    .build();

            StepVerifier.create(validator.validateRequiredDelegateTypes(ctx, new DelegateType[0]))
                    .assertNext(result -> assertThat(result).isEqualTo(ctx))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should pass when required is null")
        void shouldPassWhenRequiredIsNull() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withActiveDelegateTypes(Set.of(DelegateType.RPR))
                    .build();

            StepVerifier.create(validator.validateRequiredDelegateTypes(ctx, null))
                    .assertNext(result -> assertThat(result).isEqualTo(ctx))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should skip validation for non-DELEGATE personas")
        void shouldSkipForNonDelegatePersonas() {
            AuthContext selfCtx = anAuthContext()
                    .withPersona(Persona.SELF)
                    .build();

            DelegateType[] required = {DelegateType.RPR, DelegateType.DAA};

            StepVerifier.create(validator.validateRequiredDelegateTypes(selfCtx, required))
                    .assertNext(result -> assertThat(result.persona()).isEqualTo(Persona.SELF))
                    .verifyComplete();
        }

        @Test
        @DisplayName("should throw when missing required delegate types")
        void shouldThrowWhenMissingTypes() {
            AuthContext ctx = anAuthContext()
                    .withPersona(Persona.DELEGATE)
                    .withActiveDelegateTypes(Set.of(DelegateType.RPR))
                    .build();

            DelegateType[] required = {DelegateType.RPR, DelegateType.DAA};

            StepVerifier.create(validator.validateRequiredDelegateTypes(ctx, required))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(AuthorizationException.class);
                        assertThat(error.getMessage())
                                .contains("Missing required delegate types")
                                .contains("DAA");
                    })
                    .verify();
        }
    }
}
