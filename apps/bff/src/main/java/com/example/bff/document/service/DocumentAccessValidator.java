package com.example.bff.document.service;

import com.example.bff.security.context.AuthContext;
import com.example.bff.security.context.DelegateType;
import com.example.bff.security.context.Persona;
import com.example.bff.security.exception.AuthorizationException;
import com.example.bff.security.exception.SecurityIncidentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;

@Slf4j
@Component
public class DocumentAccessValidator {

    private static final Set<DelegateType> REQUIRED_DELEGATE_TYPES =
            Set.of(DelegateType.ROI, DelegateType.DAA, DelegateType.RPR);

    public Mono<Void> validateAccess(AuthContext auth, String documentOwnerEid) {
        return Mono.defer(() -> {
            // CONFIG_SPECIALIST has no access (should be blocked at controller, but double-check)
            if (auth.persona() == Persona.CONFIG_SPECIALIST) {
                log.warn("CONFIG_SPECIALIST attempted document access: userId={}",
                        auth.loggedInMemberIdValue());
                return Mono.error(new AuthorizationException(
                        "CONFIG_SPECIALIST persona does not have access to documents"));
            }

            // For SELF, verify the document belongs to them
            if (auth.persona() == Persona.SELF) {
                if (!auth.enterpriseId().equals(documentOwnerEid)) {
                    log.error("SECURITY INCIDENT: SELF attempted access to another member's document. " +
                                    "userId={}, attemptedDocOwner={}",
                            auth.loggedInMemberIdValue(), documentOwnerEid);
                    return Mono.error(new SecurityIncidentException(
                            "Unauthorized access attempt to document",
                            auth.loggedInMemberIdValue(),
                            documentOwnerEid
                    ));
                }
            }

            // For DELEGATE, verify they have all required delegate types
            if (auth.persona() == Persona.DELEGATE) {
                if (!auth.enterpriseId().equals(documentOwnerEid)) {
                    log.error("SECURITY INCIDENT: DELEGATE attempted access to unauthorized member's document. " +
                                    "userId={}, authorizedEid={}, attemptedDocOwner={}",
                            auth.loggedInMemberIdValue(), auth.enterpriseId(), documentOwnerEid);
                    return Mono.error(new AuthorizationException(
                            "Document owner does not match requested enterpriseId"));
                }

                if (!auth.activeDelegateTypes().containsAll(REQUIRED_DELEGATE_TYPES)) {
                    log.warn("DELEGATE missing required delegate types for document access. " +
                                    "userId={}, required={}, active={}",
                            auth.loggedInMemberIdValue(), REQUIRED_DELEGATE_TYPES, auth.activeDelegateTypes());
                    return Mono.error(new AuthorizationException(
                            "DELEGATE requires ROI, DAA, and RPR permissions for document access"));
                }
            }

            // AGENT and CASE_WORKER can access any enterpriseId (already validated by filter)
            log.debug("Document access validated: persona={}, ownerEid={}", auth.persona(), documentOwnerEid);
            return Mono.empty();
        });
    }

    public Mono<Void> validateUpload(AuthContext auth, String targetOwnerEid) {
        return validateAccess(auth, targetOwnerEid);
    }
}
