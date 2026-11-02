package fr.portfolio.helpdesk.domain;

import java.util.EnumSet;
import java.util.Set;

public enum TicketStatus {
    OPEN, IN_PROGRESS, WAITING, RESOLVED, CLOSED;

    public static final Set<TicketStatus> ACTIVE = EnumSet.of(OPEN, IN_PROGRESS, WAITING);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
