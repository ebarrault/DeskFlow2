package fr.portfolio.helpdesk.notification;

import fr.portfolio.helpdesk.config.AppProperties;
import fr.portfolio.helpdesk.domain.Role;
import fr.portfolio.helpdesk.domain.Ticket;
import fr.portfolio.helpdesk.domain.User;
import fr.portfolio.helpdesk.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Notifications par e-mail. Un échec d'envoi est journalisé mais ne bloque jamais l'action métier. */
@Component
public class MailNotifier {

    private static final Logger log = LoggerFactory.getLogger(MailNotifier.class);
    private static final Map<String, String> STATUS_FR = Map.of(
            "OPEN", "Ouvert", "IN_PROGRESS", "En cours", "WAITING", "En attente", "RESOLVED", "Résolu", "CLOSED", "Fermé");

    private final JavaMailSender sender;
    private final UserRepository users;
    private final AppProperties props;

    public MailNotifier(JavaMailSender sender, UserRepository users, AppProperties props) {
        this.sender = sender;
        this.users = users;
        this.props = props;
    }

    private String link(Ticket t) {
        return props.url() + "/tickets/" + t.getId();
    }

    void send(List<String> to, String subject, String text) {
        List<String> recipients = to.stream().filter(Objects::nonNull).distinct().toList();
        if (recipients.isEmpty()) return;
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.mailFrom());
            msg.setTo(recipients.toArray(String[]::new));
            msg.setSubject("[Helpdesk] " + subject);
            msg.setText(text);
            sender.send(msg);
        } catch (MailException e) {
            log.warn("Envoi du mail « {} » impossible : {}", subject, e.getMessage());
        }
    }

    private List<String> staffEmails(boolean adminsOnly) {
        var roles = adminsOnly ? EnumSet.of(Role.ADMIN) : EnumSet.of(Role.TECHNICIAN, Role.ADMIN);
        return users.findByActiveTrueAndRoleInOrderByNameAsc(roles).stream().map(User::getEmail).toList();
    }

    public void ticketCreated(Ticket t) {
        send(List.of(t.getRequester().getEmail()), "Ticket #" + t.getId() + " créé",
                "Bonjour " + t.getRequester().getName() + ",\n\nVotre demande « " + t.getTitle()
                        + " » a bien été enregistrée (priorité " + t.getPriority() + ").\nSuivi : " + link(t));
        send(staffEmails(false), "Nouveau ticket #" + t.getId() + " (" + t.getPriority() + ")",
                "Nouveau ticket de " + t.getRequester().getName() + " : « " + t.getTitle() + " »\n" + link(t));
    }

    public void ticketAssigned(Ticket t) {
        if (t.getAssignee() == null) return;
        send(List.of(t.getAssignee().getEmail()), "Ticket #" + t.getId() + " vous est affecté",
                "« " + t.getTitle() + " » (priorité " + t.getPriority() + ")\n" + link(t));
    }

    public void statusChanged(Ticket t) {
        String s = STATUS_FR.getOrDefault(t.getStatus().name(), t.getStatus().name());
        send(List.of(t.getRequester().getEmail()), "Ticket #" + t.getId() + " : " + s,
                "Le statut de votre demande « " + t.getTitle() + " » est maintenant : " + s + ".\n" + link(t));
    }

    public void newComment(Ticket t, String authorName, String to) {
        send(Stream.ofNullable(to).toList(), "Nouvelle réponse sur le ticket #" + t.getId(),
                authorName + " a répondu sur « " + t.getTitle() + " ».\n" + link(t));
    }

    public void slaBreached(Ticket t) {
        var to = new java.util.ArrayList<>(staffEmails(true));
        if (t.getAssignee() != null) to.add(t.getAssignee().getEmail());
        send(to, "Délai dépassé sur le ticket #" + t.getId(),
                "Le délai du ticket « " + t.getTitle() + " » (" + t.getPriority() + ") est dépassé.\n" + link(t));
    }
}
