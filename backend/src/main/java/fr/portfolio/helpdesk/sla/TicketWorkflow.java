package fr.portfolio.helpdesk.sla;

import fr.portfolio.helpdesk.domain.InvalidTransitionException;
import fr.portfolio.helpdesk.domain.TicketStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static fr.portfolio.helpdesk.domain.TicketStatus.*;

/** Cycle de vie d'un ticket : transitions de statut autorisées. */
public final class TicketWorkflow {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = new EnumMap<>(TicketStatus.class);

    static {
        ALLOWED.put(OPEN, EnumSet.of(IN_PROGRESS, WAITING, RESOLVED, CLOSED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(WAITING, RESOLVED));
        ALLOWED.put(WAITING, EnumSet.of(IN_PROGRESS, RESOLVED));
        ALLOWED.put(RESOLVED, EnumSet.of(CLOSED, IN_PROGRESS)); // réouverture possible
        ALLOWED.put(CLOSED, EnumSet.noneOf(TicketStatus.class));
    }

    private TicketWorkflow() {
    }

    public static Set<TicketStatus> nextStatuses(TicketStatus from) {
        return EnumSet.copyOf(ALLOWED.get(from));
    }

    public static boolean canTransition(TicketStatus from, TicketStatus to) {
        return from == to || ALLOWED.get(from).contains(to);
    }

    public static void assertTransition(TicketStatus from, TicketStatus to) {
        if (!canTransition(from, to)) {
            throw new InvalidTransitionException(from, to);
        }
    }
}
