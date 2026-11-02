package fr.portfolio.helpdesk.ticket;

import fr.portfolio.helpdesk.common.ApiException;
import fr.portfolio.helpdesk.domain.*;
import fr.portfolio.helpdesk.notification.MailNotifier;
import fr.portfolio.helpdesk.security.AuthUser;
import fr.portfolio.helpdesk.sla.SlaPolicy;
import fr.portfolio.helpdesk.sla.TicketWorkflow;
import fr.portfolio.helpdesk.ticket.TicketDtos.*;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;

@Service
public class TicketService {

    private final TicketRepository tickets;
    private final UserRepository users;
    private final AttachmentRepository attachments;
    private final FileStorageService storage;
    private final MailNotifier notifier;
    private final Clock clock;

    public TicketService(TicketRepository tickets, UserRepository users, AttachmentRepository attachments,
                         FileStorageService storage, MailNotifier notifier, Clock clock) {
        this.tickets = tickets;
        this.users = users;
        this.attachments = attachments;
        this.storage = storage;
        this.notifier = notifier;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketSummary> findAll(AuthUser user, TicketFilter filter, int page, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        var result = tickets.findAll(TicketSpecifications.of(user, filter),
                PageRequest.of(Math.max(page, 1) - 1, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        Instant now = clock.instant();
        return new PageResponse<>(result.map(t -> TicketDtos.summary(t, now)).getContent(), result.getTotalElements(), Math.max(page, 1), size);
    }

    @Transactional(readOnly = true)
    public TicketDetail findOne(AuthUser user, Long id) {
        Ticket t = load(id);
        assertCanAccess(user, t);
        return TicketDtos.detail(t, user.isStaff(), clock.instant());
    }

    @Transactional
    public TicketSummary create(AuthUser user, CreateTicketRequest req) {
        User requester = users.getReferenceById(user.id());
        Priority priority = req.priority() == null ? Priority.MEDIUM : req.priority();
        Instant now = clock.instant();
        var due = SlaPolicy.dueDates(priority, now);

        Ticket t = new Ticket();
        t.setTitle(req.title().trim());
        t.setDescription(req.description());
        t.setCategory(req.category());
        t.setPriority(priority);
        t.setRequester(requester);
        t.setCreatedAt(now);
        t.setResponseDueAt(due.responseDueAt());
        t.setResolutionDueAt(due.resolutionDueAt());
        t.addEvent(EventType.CREATED, requester, null, null);
        t = tickets.save(t);

        notifier.ticketCreated(t);
        return TicketDtos.summary(t, now);
    }

    /** Statut, priorité, affectation : réservé aux techniciens et administrateurs. */
    @Transactional
    public TicketSummary update(AuthUser user, Long id, UpdateTicketRequest req) {
        if (!user.isStaff()) throw ApiException.forbidden("Réservé aux techniciens");
        Ticket t = load(id);
        User actor = users.getReferenceById(user.id());
        Instant now = clock.instant();
        boolean statusChanged = false;
        boolean assigned = false;

        if (req.status() != null && req.status() != t.getStatus()) {
            TicketStatus from = t.getStatus();
            TicketStatus to = req.status();
            TicketWorkflow.assertTransition(from, to);
            t.setStatus(to);
            if (to == TicketStatus.RESOLVED) t.setResolvedAt(now);
            if (to == TicketStatus.CLOSED) {
                t.setClosedAt(now);
                if (t.getResolvedAt() == null) t.setResolvedAt(now);
            }
            if (from == TicketStatus.RESOLVED && to == TicketStatus.IN_PROGRESS) t.setResolvedAt(null); // réouverture
            if (to == TicketStatus.IN_PROGRESS && t.getFirstResponseAt() == null) t.setFirstResponseAt(now);
            t.addEvent(EventType.STATUS_CHANGED, actor, from.name(), to.name());
            statusChanged = true;
        }

        if (req.priority() != null && req.priority() != t.getPriority()) {
            var due = SlaPolicy.dueDates(req.priority(), t.getCreatedAt()); // recalcul depuis la création
            t.addEvent(EventType.PRIORITY_CHANGED, actor, t.getPriority().name(), req.priority().name());
            t.setPriority(req.priority());
            t.setResponseDueAt(due.responseDueAt());
            t.setResolutionDueAt(due.resolutionDueAt());
        }

        if (Boolean.TRUE.equals(req.unassign()) && t.getAssignee() != null) {
            t.setAssignee(null);
            t.addEvent(EventType.ASSIGNED, actor, null, null);
        } else if (req.assigneeId() != null && (t.getAssignee() == null || !req.assigneeId().equals(t.getAssignee().getId()))) {
            User assignee = users.findById(req.assigneeId())
                    .filter(u -> u.isActive() && u.getRole().isStaff())
                    .orElseThrow(() -> ApiException.badRequest("Le ticket doit être affecté à un technicien actif"));
            t.setAssignee(assignee);
            t.addEvent(EventType.ASSIGNED, actor, null, assignee.getName());
            assigned = true;
        }

        if (req.category() != null) t.setCategory(req.category());

        if (statusChanged) notifier.statusChanged(t);
        if (assigned) notifier.ticketAssigned(t);
        return TicketDtos.summary(t, now);
    }

    @Transactional
    public CommentView addComment(AuthUser user, Long id, CommentRequest req) {
        Ticket t = load(id);
        assertCanAccess(user, t);
        if (t.getStatus() == TicketStatus.CLOSED) throw ApiException.badRequest("Ticket fermé");

        boolean internal = user.isStaff() && Boolean.TRUE.equals(req.internal()); // un utilisateur ne peut pas écrire de note interne
        User author = users.getReferenceById(user.id());
        Comment c = new Comment(t, author, req.body(), internal);
        t.getComments().add(c);
        t.addEvent(EventType.COMMENTED, author, null, internal ? "interne" : null);
        // La première réponse publique d'un technicien arrête le chrono de prise en charge
        if (user.isStaff() && !internal && t.getFirstResponseAt() == null) t.setFirstResponseAt(clock.instant());
        tickets.saveAndFlush(t);

        if (!internal) {
            String to = user.isStaff() ? t.getRequester().getEmail() : (t.getAssignee() != null ? t.getAssignee().getEmail() : null);
            notifier.newComment(t, user.name(), to);
        }
        return new CommentView(c.getId(), c.getBody(), c.isInternal(), c.getCreatedAt(), new UserRef(user.id(), user.name(), user.email()), user.role());
    }

    @Transactional
    public AttachmentView addAttachment(AuthUser user, Long id, MultipartFile file) {
        Ticket t = load(id);
        assertCanAccess(user, t);
        String stored = storage.store(file);
        User uploader = users.getReferenceById(user.id());

        Attachment a = new Attachment();
        a.setTicket(t);
        a.setUploader(uploader);
        a.setOriginalName(file.getOriginalFilename() == null ? stored : file.getOriginalFilename());
        a.setStoredName(stored);
        a.setMimeType(file.getContentType());
        a.setSize(file.getSize());
        t.getAttachments().add(a);
        t.addEvent(EventType.ATTACHMENT_ADDED, uploader, null, a.getOriginalName());
        tickets.saveAndFlush(t);
        return new AttachmentView(a.getId(), a.getOriginalName(), a.getMimeType(), a.getSize(), a.getCreatedAt(), new UserRef(user.id(), user.name(), user.email()));
    }

    public record Download(Resource resource, String name, String mimeType) {}

    @Transactional(readOnly = true)
    public Download download(AuthUser user, Long ticketId, Long attachmentId) {
        Attachment a = attachments.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> ApiException.notFound("Pièce jointe introuvable"));
        assertCanAccess(user, a.getTicket());
        return new Download(storage.load(a.getStoredName()), a.getOriginalName(), a.getMimeType());
    }

    private Ticket load(Long id) {
        return tickets.findById(id).orElseThrow(() -> ApiException.notFound("Ticket introuvable"));
    }

    static void assertCanAccess(AuthUser user, Ticket t) {
        if (!user.isStaff() && !t.getRequester().getId().equals(user.id())) {
            throw ApiException.forbidden("Vous n'avez pas accès à ce ticket");
        }
    }
}
