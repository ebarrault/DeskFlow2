package fr.portfolio.helpdesk.domain;

public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(TicketStatus from, TicketStatus to) {
        super("Transition interdite : " + from + " → " + to);
    }
}
