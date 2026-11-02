package fr.portfolio.helpdesk.sla;

import fr.portfolio.helpdesk.domain.Priority;
import fr.portfolio.helpdesk.domain.SlaState;
import fr.portfolio.helpdesk.domain.TicketStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Délais contractuels par priorité (heures calendaires) et calcul de l'état du SLA.
 * Classe sans dépendance au framework : entièrement testable unitairement.
 */
public final class SlaPolicy {

    public record Targets(Duration response, Duration resolution) {}

    public record DueDates(Instant responseDueAt, Instant resolutionDueAt) {}

    /** Instantané des dates d'un ticket nécessaires au calcul. */
    public record Timeline(TicketStatus status, Instant createdAt, Instant responseDueAt, Instant resolutionDueAt,
                           Instant firstResponseAt, Instant resolvedAt) {}

    private static final Map<Priority, Targets> TARGETS = new EnumMap<>(Priority.class);

    static {
        TARGETS.put(Priority.CRITICAL, new Targets(Duration.ofHours(1), Duration.ofHours(4)));
        TARGETS.put(Priority.HIGH, new Targets(Duration.ofHours(2), Duration.ofHours(8)));
        TARGETS.put(Priority.MEDIUM, new Targets(Duration.ofHours(4), Duration.ofHours(24)));
        TARGETS.put(Priority.LOW, new Targets(Duration.ofHours(8), Duration.ofHours(72)));
    }

    /** Seuil à partir duquel un ticket est « bientôt en retard » (part du délai de résolution écoulée). */
    static final double AT_RISK_RATIO = 0.75;

    private SlaPolicy() {
    }

    public static Targets targets(Priority priority) {
        return TARGETS.get(priority);
    }

    public static DueDates dueDates(Priority priority, Instant from) {
        Targets t = TARGETS.get(priority);
        return new DueDates(from.plus(t.response()), from.plus(t.resolution()));
    }

    public static SlaState state(Timeline t, Instant now) {
        if (t.resolvedAt() != null) {
            return t.resolvedAt().isAfter(t.resolutionDueAt()) ? SlaState.MISSED : SlaState.MET;
        }
        if (t.status() == TicketStatus.CLOSED) {
            return SlaState.MET;
        }
        boolean responseLate = t.firstResponseAt() == null && now.isAfter(t.responseDueAt());
        if (responseLate || now.isAfter(t.resolutionDueAt())) {
            return SlaState.BREACHED;
        }
        long total = Duration.between(t.createdAt(), t.resolutionDueAt()).toMillis();
        long elapsed = Duration.between(t.createdAt(), now).toMillis();
        return total > 0 && (double) elapsed / total >= AT_RISK_RATIO ? SlaState.AT_RISK : SlaState.OK;
    }
}
