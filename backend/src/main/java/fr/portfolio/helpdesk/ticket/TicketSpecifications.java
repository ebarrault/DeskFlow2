package fr.portfolio.helpdesk.ticket;

import fr.portfolio.helpdesk.domain.Ticket;
import fr.portfolio.helpdesk.security.AuthUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Filtres dynamiques de la liste des tickets (API Criteria). */
final class TicketSpecifications {
    private TicketSpecifications() {
    }

    static Specification<Ticket> of(AuthUser user, TicketDtos.TicketFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!user.isStaff()) p.add(cb.equal(root.get("requester").get("id"), user.id())); // un utilisateur ne voit que ses demandes
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            if (f.priority() != null) p.add(cb.equal(root.get("priority"), f.priority()));
            if (f.assigneeId() != null) p.add(cb.equal(root.get("assignee").get("id"), f.assigneeId()));
            if (Boolean.TRUE.equals(f.unassigned())) p.add(cb.isNull(root.get("assignee")));
            if (Boolean.TRUE.equals(f.slaBreached())) p.add(cb.isTrue(root.get("slaBreached")));
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("title")), like, '\\'), cb.like(cb.lower(root.get("description")), like, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
