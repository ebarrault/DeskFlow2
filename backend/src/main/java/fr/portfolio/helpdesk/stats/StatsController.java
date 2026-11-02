package fr.portfolio.helpdesk.stats;

import fr.portfolio.helpdesk.domain.Priority;
import fr.portfolio.helpdesk.domain.Ticket;
import fr.portfolio.helpdesk.domain.TicketRepository;
import fr.portfolio.helpdesk.domain.TicketStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Indicateurs du tableau de bord (techniciens et administrateurs, voir SecurityConfig). */
@RestController
@RequestMapping("/api/stats")
@Tag(name = "stats")
public class StatsController {

    public record Count<T>(T key, long count) {}

    public record Day(LocalDate date, long created, long resolved) {}

    public record Overview(long activeTickets, long breachedTickets, Double slaCompliance, Double avgResolutionHours,
                           List<Count<TicketStatus>> byStatus, List<Count<Priority>> byPriority,
                           List<Day> perDay, List<Count<String>> workload) {}

    private final TicketRepository tickets;
    private final Clock clock;

    public StatsController(TicketRepository tickets, Clock clock) {
        this.tickets = tickets;
        this.clock = clock;
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public Overview overview() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        Instant since = today.minusDays(30).atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Ticket> resolved = tickets.findByResolvedAtAfter(since);
        long within = resolved.stream().filter(t -> !t.getResolvedAt().isAfter(t.getResolutionDueAt())).count();
        Double compliance = resolved.isEmpty() ? null : Math.round(1000.0 * within / resolved.size()) / 10.0;
        Double avgHours = resolved.isEmpty() ? null : Math.round(10.0 * resolved.stream()
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes()).average().orElse(0) / 60.0) / 10.0;

        Map<LocalDate, long[]> days = new LinkedHashMap<>();
        for (int i = 30; i >= 0; i--) days.put(today.minusDays(i), new long[2]);
        tickets.findByCreatedAtAfter(since).forEach(t -> {
            long[] d = days.get(LocalDate.ofInstant(t.getCreatedAt(), ZoneOffset.UTC));
            if (d != null) d[0]++;
        });
        resolved.forEach(t -> {
            long[] d = days.get(LocalDate.ofInstant(t.getResolvedAt(), ZoneOffset.UTC));
            if (d != null) d[1]++;
        });

        return new Overview(
                tickets.countByStatusIn(TicketStatus.ACTIVE),
                tickets.countByStatusInAndSlaBreachedTrue(TicketStatus.ACTIVE),
                compliance,
                avgHours,
                tickets.countGroupByStatus().stream().map(r -> new Count<>((TicketStatus) r[0], (Long) r[1])).toList(),
                tickets.countActiveGroupByPriority(TicketStatus.ACTIVE).stream().map(r -> new Count<>((Priority) r[0], (Long) r[1])).toList(),
                days.entrySet().stream().map(e -> new Day(e.getKey(), e.getValue()[0], e.getValue()[1])).toList(),
                tickets.countActiveGroupByAssignee(TicketStatus.ACTIVE).stream().map(r -> new Count<>((String) r[0], (Long) r[1])).toList());
    }
}
