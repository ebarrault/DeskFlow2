package fr.portfolio.helpdesk.sla;

import fr.portfolio.helpdesk.domain.Priority;
import fr.portfolio.helpdesk.domain.SlaState;
import fr.portfolio.helpdesk.domain.TicketStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlaPolicyTest {

    private static final Instant T0 = Instant.parse("2026-01-05T08:00:00Z");

    private static Instant h(double hours) {
        return T0.plus(Duration.ofMinutes((long) (hours * 60)));
    }

    private static SlaPolicy.Timeline timeline(Priority p, Instant firstResponse, Instant resolved) {
        var due = SlaPolicy.dueDates(p, T0);
        return new SlaPolicy.Timeline(resolved == null ? TicketStatus.IN_PROGRESS : TicketStatus.RESOLVED,
                T0, due.responseDueAt(), due.resolutionDueAt(), firstResponse, resolved);
    }

    @Test
    @DisplayName("échéances calculées selon la priorité")
    void dueDates() {
        var critical = SlaPolicy.dueDates(Priority.CRITICAL, T0);
        assertEquals(h(1), critical.responseDueAt());
        assertEquals(h(4), critical.resolutionDueAt());
        assertEquals(h(72), SlaPolicy.dueDates(Priority.LOW, T0).resolutionDueAt());
    }

    @Test
    @DisplayName("OK au début, AT_RISK après 75 % du délai de résolution")
    void atRisk() {
        var t = timeline(Priority.HIGH, h(1), null);
        assertEquals(SlaState.OK, SlaPolicy.state(t, h(2)));
        assertEquals(SlaState.AT_RISK, SlaPolicy.state(t, h(6.5)));
    }

    @Test
    @DisplayName("BREACHED si la prise en charge est en retard")
    void responseLate() {
        assertEquals(SlaState.BREACHED, SlaPolicy.state(timeline(Priority.MEDIUM, null, null), h(5)));
    }

    @Test
    @DisplayName("BREACHED si la résolution est en retard malgré la prise en charge")
    void resolutionLate() {
        assertEquals(SlaState.BREACHED, SlaPolicy.state(timeline(Priority.CRITICAL, h(0.5), null), h(5)));
    }

    @Test
    @DisplayName("MET ou MISSED pour un ticket résolu")
    void resolved() {
        assertEquals(SlaState.MET, SlaPolicy.state(timeline(Priority.HIGH, h(1), h(7)), h(100)));
        assertEquals(SlaState.MISSED, SlaPolicy.state(timeline(Priority.HIGH, h(1), h(9)), h(100)));
    }
}
