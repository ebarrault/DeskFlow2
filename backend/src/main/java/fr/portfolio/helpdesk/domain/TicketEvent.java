package fr.portfolio.helpdesk.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Historique : une ligne par action sur le ticket. */
@Entity
@Table(name = "ticket_events")
@Getter
@NoArgsConstructor
public class TicketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType type;

    @Column(name = "from_value")
    private String fromValue;

    @Column(name = "to_value")
    private String toValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public TicketEvent(Ticket ticket, User actor, EventType type, String fromValue, String toValue) {
        this.ticket = ticket;
        this.actor = actor;
        this.type = type;
        this.fromValue = fromValue;
        this.toValue = toValue;
    }
}
