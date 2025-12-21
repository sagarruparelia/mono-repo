package com.example.bff.session.pubsub;

import com.example.bff.common.util.StringSanitizer;
import com.example.bff.session.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * Handles session events received from other BFF instances via Pub/Sub.
 * Performs local session cleanup and cache invalidation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.session.pubsub.enabled", havingValue = "true")
public class SessionEventHandler {

    private final SessionService sessionService;

    /**
     * Handles session invalidation from another instance.
     * In a future enhancement, this could clear local caches.
     */
    public void handleInvalidation(@NonNull SessionEventMessage message) {
        log.info("Processing remote session invalidation: session={}, reason={}",
                StringSanitizer.forLog(message.sessionId()),
                StringSanitizer.forLog(message.reason()));

        // For now, Redis is the source of truth, so other instances
        // will see the invalidation on next request.
        // Future enhancement: clear local permission caches here
    }

    /**
     * Handles force logout event - invalidates all sessions for a user.
     */
    public void handleForceLogout(@NonNull SessionEventMessage message) {
        if (message.hsidUuid() == null || message.hsidUuid().isBlank()) {
            log.warn("Force logout event missing hsidUuid");
            return;
        }

        log.info("Processing remote force logout: hsidUuid={}, reason={}",
                StringSanitizer.forLog(message.hsidUuid()),
                StringSanitizer.forLog(message.reason()));

        // Invalidate all sessions for the user
        sessionService.invalidateExistingSessions(message.hsidUuid())
                .doOnSuccess(v -> log.debug("Force logout completed for hsidUuid: {}",
                        StringSanitizer.forLog(message.hsidUuid())))
                .doOnError(e -> log.error("Force logout failed for hsidUuid {}: {}",
                        StringSanitizer.forLog(message.hsidUuid()),
                        StringSanitizer.forLog(e.getMessage())))
                .subscribe();
    }

    /**
     * Handles session rotation event from another instance.
     * In a future enhancement, this could update local session caches.
     */
    public void handleRotation(@NonNull SessionEventMessage message) {
        log.debug("Processing remote session rotation: oldSession={}, hsidUuid={}",
                StringSanitizer.forLog(message.sessionId()),
                StringSanitizer.forLog(message.hsidUuid()));

        // Redis handles the rotation atomically - this is for future local cache invalidation
    }
}
