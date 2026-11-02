package fr.portfolio.helpdesk.config;

import fr.portfolio.helpdesk.domain.*;
import fr.portfolio.helpdesk.sla.SlaPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Comptes et tickets de démonstration (activé par SEED=true). */
@Component
@ConditionalOnProperty(name = "app.seed", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository users;
    private final TicketRepository tickets;
    private final PasswordEncoder encoder;

    public DemoDataSeeder(UserRepository users, TicketRepository tickets, PasswordEncoder encoder) {
        this.users = users;
        this.tickets = tickets;
        this.encoder = encoder;
    }

    private User user(String email, String name, String password, Role role) {
        return users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(new User(email, name, encoder.encode(password), role)));
    }

    @Override
    @Transactional
    public void run(String... args) {
        user("admin@helpdesk.local", "Alice Admin", "Admin123!", Role.ADMIN);
        User tech = user("tech@helpdesk.local", "Thomas Technicien", "Tech123!", Role.TECHNICIAN);
        User requester = user("user@helpdesk.local", "Ugo Utilisateur", "User123!", Role.USER);
        if (tickets.count() > 0) return;

        record Sample(String title, String description, Priority priority, TicketStatus status, String category, long hoursAgo) {}
        var samples = new Sample[]{
                new Sample("Imprimante du 2e étage hors ligne", "L'imprimante HP du 2e étage n'apparaît plus sur le réseau depuis ce matin.", Priority.MEDIUM, TicketStatus.OPEN, "Matériel", 2),
                new Sample("Accès VPN impossible", "Message « authentification échouée » à chaque tentative de connexion au VPN.", Priority.HIGH, TicketStatus.IN_PROGRESS, "Réseau", 5),
                new Sample("Messagerie inaccessible pour la compta", "Outlook ne se connecte plus pour les 6 postes de la comptabilité.", Priority.CRITICAL, TicketStatus.RESOLVED, "Messagerie", 30),
                new Sample("Demande de second écran", "Besoin d'un second écran pour le poste PC-0142.", Priority.LOW, TicketStatus.WAITING, "Matériel", 50),
                new Sample("Mot de passe expiré", "Mon mot de passe Windows a expiré, je ne peux plus ouvrir de session.", Priority.MEDIUM, TicketStatus.CLOSED, "Comptes", 80),
        };
        for (Sample s : samples) {
            Instant created = Instant.now().minus(Duration.ofHours(s.hoursAgo()));
            var due = SlaPolicy.dueDates(s.priority(), created);
            Ticket t = new Ticket();
            t.setTitle(s.title());
            t.setDescription(s.description());
            t.setPriority(s.priority());
            t.setStatus(s.status());
            t.setCategory(s.category());
            t.setRequester(requester);
            t.setCreatedAt(created);
            t.setResponseDueAt(due.responseDueAt());
            t.setResolutionDueAt(due.resolutionDueAt());
            if (s.status() != TicketStatus.OPEN) {
                t.setAssignee(tech);
                t.setFirstResponseAt(created.plus(Duration.ofMinutes(30)));
                t.getComments().add(new Comment(t, tech, "Pris en charge, je regarde.", false));
            }
            if (s.status() == TicketStatus.RESOLVED || s.status() == TicketStatus.CLOSED) t.setResolvedAt(created.plus(Duration.ofHours(3)));
            if (s.status() == TicketStatus.CLOSED) t.setClosedAt(created.plus(Duration.ofHours(4)));
            t.addEvent(EventType.CREATED, requester, null, null);
            tickets.save(t);
        }
        log.info("Données de démonstration créées — admin@helpdesk.local / Admin123!");
    }
}
