package fr.portfolio.helpdesk.ticket;

import fr.portfolio.helpdesk.domain.EventType;
import fr.portfolio.helpdesk.domain.SlaState;
import fr.portfolio.helpdesk.domain.Ticket;
import fr.portfolio.helpdesk.domain.TicketRepository;
import fr.portfolio.helpdesk.domain.TicketStatus;
import fr.portfolio.helpdesk.notification.MailNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Toutes les 5 minutes : repère les tickets dont le délai vient d'être dépassé et prévient. */
@Component
public class SlaScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaScheduler.class);

    private final TicketRepository tickets;
    private final MailNotifier notifier;
    private final Clock clock;

    public SlaScheduler(TicketRepository tickets, MailNotifier notifier, Clock clock) {
        this.tickets = tickets;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @Transactional
    public int checkBreaches() {
        Instant now = clock.instant();
        int count = 0;
        for (Ticket t : tickets.findBySlaBreachedFalseAndStatusIn(TicketStatus.ACTIVE)) {
            if (TicketDtos.sla(t, now) == SlaState.BREACHED) {
                t.setSlaBreached(true);
                t.addEvent(EventType.SLA_BREACHED, null, null, null);
                notifier.slaBreached(t);
                count++;
            }
        }
        if (count > 0) log.info("{} ticket(s) en dépassement de délai", count);
        return count;
    }
}
