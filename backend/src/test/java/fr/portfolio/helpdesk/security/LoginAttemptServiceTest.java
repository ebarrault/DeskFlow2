package fr.portfolio.helpdesk.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptServiceTest {

    /** Horloge réglable pour simuler le passage du temps. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-01-01T10:00:00Z");

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    @DisplayName("bloque après 5 échecs, débloque après une minute")
    void blocksThenReleases() {
        var clock = new MutableClock();
        var service = new LoginAttemptService(clock);
        for (int i = 0; i < 4; i++) service.failed("Alice@test");
        assertFalse(service.isBlocked("alice@test"));
        service.failed("alice@test");
        assertTrue(service.isBlocked("ALICE@test"));
        clock.now = clock.now.plus(Duration.ofSeconds(61));
        assertFalse(service.isBlocked("alice@test"));
    }

    @Test
    @DisplayName("une connexion réussie remet le compteur à zéro")
    void resetOnSuccess() {
        var service = new LoginAttemptService(new MutableClock());
        for (int i = 0; i < 5; i++) service.failed("bob@test");
        service.succeeded("bob@test");
        assertFalse(service.isBlocked("bob@test"));
    }
}
