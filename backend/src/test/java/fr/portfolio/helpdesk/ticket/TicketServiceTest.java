package fr.portfolio.helpdesk.ticket;

import fr.portfolio.helpdesk.common.ApiException;
import fr.portfolio.helpdesk.domain.*;
import fr.portfolio.helpdesk.notification.MailNotifier;
import fr.portfolio.helpdesk.security.AuthUser;
import fr.portfolio.helpdesk.ticket.TicketDtos.CommentRequest;
import fr.portfolio.helpdesk.ticket.TicketDtos.UpdateTicketRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock TicketRepository tickets;
    @Mock UserRepository users;
    @Mock AttachmentRepository attachments;
    @Mock FileStorageService storage;
    @Mock MailNotifier notifier;

    final Instant now = Instant.parse("2026-03-02T09:00:00Z");
    TicketService service;

    final AuthUser requester = new AuthUser(10L, "u@test", "Ugo", Role.USER);
    final AuthUser other = new AuthUser(11L, "o@test", "Olga", Role.USER);
    final AuthUser tech = new AuthUser(20L, "t@test", "Thomas", Role.TECHNICIAN);

    @BeforeEach
    void setUp() {
        service = new TicketService(tickets, users, attachments, storage, notifier, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static User user(long id, Role role) {
        User u = new User(id + "@test", "User " + id, "hash", role);
        u.setId(id);
        return u;
    }

    private Ticket ticket(TicketStatus status) {
        Ticket t = new Ticket();
        t.setId(1L);
        t.setTitle("Imprimante");
        t.setDescription("Ne répond plus");
        t.setStatus(status);
        t.setPriority(Priority.MEDIUM);
        t.setRequester(user(10, Role.USER));
        t.setCreatedAt(now.minusSeconds(3600));
        t.setResponseDueAt(now.plusSeconds(3 * 3600));
        t.setResolutionDueAt(now.plusSeconds(23 * 3600));
        return t;
    }

    @Test
    @DisplayName("un utilisateur ne peut pas lire le ticket d'un autre")
    void accessControl() {
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN)));
        assertThatThrownBy(() -> service.findOne(other, 1L))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(service.findOne(requester, 1L).title()).isEqualTo("Imprimante");
        assertThat(service.findOne(tech, 1L)).isNotNull();
    }

    @Test
    @DisplayName("le demandeur ne voit pas les notes internes")
    void internalNotesHidden() {
        Ticket t = ticket(TicketStatus.IN_PROGRESS);
        t.getComments().add(new Comment(t, user(20, Role.TECHNICIAN), "Note interne", true));
        t.getComments().add(new Comment(t, user(20, Role.TECHNICIAN), "Réponse publique", false));
        when(tickets.findById(1L)).thenReturn(Optional.of(t));
        assertThat(service.findOne(requester, 1L).comments()).extracting("body").containsExactly("Réponse publique");
        assertThat(service.findOne(tech, 1L).comments()).hasSize(2);
    }

    @Test
    @DisplayName("un utilisateur ne peut pas modifier le statut")
    void onlyStaffUpdates() {
        assertThatThrownBy(() -> service.update(requester, 1L, new UpdateTicketRequest(TicketStatus.CLOSED, null, null, null, null)))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(tickets);
    }

    @Test
    @DisplayName("transition interdite refusée")
    void invalidTransition() {
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket(TicketStatus.CLOSED)));
        assertThatThrownBy(() -> service.update(tech, 1L, new UpdateTicketRequest(TicketStatus.IN_PROGRESS, null, null, null, null)))
                .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    @DisplayName("passer en cours renseigne la prise en charge, trace l'historique et notifie")
    void startProgress() {
        Ticket t = ticket(TicketStatus.OPEN);
        when(tickets.findById(1L)).thenReturn(Optional.of(t));
        when(users.getReferenceById(20L)).thenReturn(user(20, Role.TECHNICIAN));

        service.update(tech, 1L, new UpdateTicketRequest(TicketStatus.IN_PROGRESS, null, null, null, null));

        assertThat(t.getStatus()).isEqualTo(TicketStatus.IN_PROGRESS);
        assertThat(t.getFirstResponseAt()).isEqualTo(now);
        assertThat(t.getEvents()).singleElement()
                .satisfies(e -> {
                    assertThat(e.getType()).isEqualTo(EventType.STATUS_CHANGED);
                    assertThat(e.getFromValue()).isEqualTo("OPEN");
                    assertThat(e.getToValue()).isEqualTo("IN_PROGRESS");
                });
        verify(notifier).statusChanged(t);
    }

    @Test
    @DisplayName("changer la priorité recalcule les échéances depuis la création")
    void priorityRecomputesDueDates() {
        Ticket t = ticket(TicketStatus.OPEN);
        when(tickets.findById(1L)).thenReturn(Optional.of(t));
        when(users.getReferenceById(20L)).thenReturn(user(20, Role.TECHNICIAN));

        service.update(tech, 1L, new UpdateTicketRequest(null, Priority.CRITICAL, null, null, null));

        assertThat(t.getResolutionDueAt()).isEqualTo(t.getCreatedAt().plusSeconds(4 * 3600));
    }

    @Test
    @DisplayName("affectation refusée à un simple utilisateur")
    void assignOnlyToStaff() {
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket(TicketStatus.OPEN)));
        when(users.getReferenceById(20L)).thenReturn(user(20, Role.TECHNICIAN));
        when(users.findById(11L)).thenReturn(Optional.of(user(11, Role.USER)));
        assertThatThrownBy(() -> service.update(tech, 1L, new UpdateTicketRequest(null, null, 11L, null, null)))
                .hasMessageContaining("technicien");
        verify(notifier, never()).ticketAssigned(any());
    }

    @Test
    @DisplayName("une « note interne » écrite par un utilisateur est publique")
    void userCannotWriteInternalNote() {
        Ticket t = ticket(TicketStatus.OPEN);
        when(tickets.findById(1L)).thenReturn(Optional.of(t));
        when(users.getReferenceById(10L)).thenReturn(t.getRequester());

        var c = service.addComment(requester, 1L, new CommentRequest("Bonjour", true));

        assertThat(c.internal()).isFalse();
        assertThat(t.getFirstResponseAt()).isNull(); // seul un technicien arrête le chrono
    }

    @Test
    @DisplayName("la première réponse publique d'un technicien arrête le chrono de prise en charge")
    void firstResponse() {
        Ticket t = ticket(TicketStatus.OPEN);
        when(tickets.findById(1L)).thenReturn(Optional.of(t));
        when(users.getReferenceById(20L)).thenReturn(user(20, Role.TECHNICIAN));

        service.addComment(tech, 1L, new CommentRequest("Je regarde", false));

        assertThat(t.getFirstResponseAt()).isEqualTo(now);
        verify(notifier).newComment(t, "Thomas", "10@test");
    }
}
