package fr.portfolio.helpdesk.sla;

import fr.portfolio.helpdesk.domain.InvalidTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static fr.portfolio.helpdesk.domain.TicketStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class TicketWorkflowTest {

    @Test
    @DisplayName("transitions normales autorisées")
    void allowed() {
        assertTrue(TicketWorkflow.canTransition(OPEN, IN_PROGRESS));
        assertTrue(TicketWorkflow.canTransition(IN_PROGRESS, RESOLVED));
        assertTrue(TicketWorkflow.canTransition(RESOLVED, CLOSED));
        assertTrue(TicketWorkflow.canTransition(RESOLVED, IN_PROGRESS));
    }

    @Test
    @DisplayName("un ticket fermé ne peut pas être rouvert")
    void closedIsFinal() {
        assertFalse(TicketWorkflow.canTransition(CLOSED, OPEN));
        assertTrue(TicketWorkflow.nextStatuses(CLOSED).isEmpty());
        assertThrows(InvalidTransitionException.class, () -> TicketWorkflow.assertTransition(CLOSED, IN_PROGRESS));
    }

    @Test
    @DisplayName("impossible de revenir à OPEN")
    void noBackToOpen() {
        assertFalse(TicketWorkflow.canTransition(IN_PROGRESS, OPEN));
        assertEquals(2, TicketWorkflow.nextStatuses(IN_PROGRESS).size());
    }
}
