package fr.portfolio.helpdesk.ticket;

import fr.portfolio.helpdesk.domain.*;
import fr.portfolio.helpdesk.sla.SlaPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class TicketDtos {
    private TicketDtos() {
    }

    // ---------- requêtes ----------

    public record CreateTicketRequest(
            @NotBlank @Size(min = 5, max = 150) String title,
            @NotBlank @Size(min = 10, max = 10_000) String description,
            Priority priority,
            @Size(max = 50) String category) {}

    /**
     * Mise à jour partielle. Pour l'affectation : assigneeId = id du technicien,
     * ou unassign = true pour retirer l'affectation.
     */
    public record UpdateTicketRequest(TicketStatus status, Priority priority, Long assigneeId, Boolean unassign,
                                      @Size(max = 50) String category) {}

    public record CommentRequest(@NotBlank @Size(max = 10_000) String body, Boolean internal) {}

    public record TicketFilter(TicketStatus status, Priority priority, Long assigneeId, Boolean unassigned,
                               Boolean slaBreached, String search) {}

    // ---------- vues ----------

    public record UserRef(Long id, String name, String email) {
        static UserRef of(User u) {
            return u == null ? null : new UserRef(u.getId(), u.getName(), u.getEmail());
        }
    }

    public record TicketSummary(Long id, String title, String category, TicketStatus status, Priority priority,
                                UserRef requester, UserRef assignee, Instant responseDueAt, Instant resolutionDueAt,
                                Instant firstResponseAt, Instant resolvedAt, Instant createdAt, SlaState sla) {}

    public record CommentView(Long id, String body, boolean internal, Instant createdAt, UserRef author, Role authorRole) {}

    public record AttachmentView(Long id, String originalName, String mimeType, long size, Instant createdAt, UserRef uploader) {}

    public record EventView(Long id, EventType type, String fromValue, String toValue, Instant createdAt, UserRef actor) {}

    public record TicketDetail(Long id, String title, String description, String category, TicketStatus status,
                               Priority priority, UserRef requester, UserRef assignee, Instant responseDueAt,
                               Instant resolutionDueAt, Instant firstResponseAt, Instant resolvedAt, Instant closedAt,
                               Instant createdAt, SlaState sla, List<TicketStatus> nextStatuses,
                               List<CommentView> comments, List<AttachmentView> attachments, List<EventView> events) {}

    public record PageResponse<T>(List<T> items, long total, int page, int pageSize) {}

    // ---------- conversions ----------

    static SlaState sla(Ticket t, Instant now) {
        return SlaPolicy.state(new SlaPolicy.Timeline(t.getStatus(), t.getCreatedAt(), t.getResponseDueAt(),
                t.getResolutionDueAt(), t.getFirstResponseAt(), t.getResolvedAt()), now);
    }

    static TicketSummary summary(Ticket t, Instant now) {
        return new TicketSummary(t.getId(), t.getTitle(), t.getCategory(), t.getStatus(), t.getPriority(),
                UserRef.of(t.getRequester()), UserRef.of(t.getAssignee()), t.getResponseDueAt(), t.getResolutionDueAt(),
                t.getFirstResponseAt(), t.getResolvedAt(), t.getCreatedAt(), sla(t, now));
    }

    static TicketDetail detail(Ticket t, boolean staff, Instant now) {
        var comments = t.getComments().stream()
                .filter(c -> staff || !c.isInternal()) // le demandeur ne voit pas les notes internes
                .map(c -> new CommentView(c.getId(), c.getBody(), c.isInternal(), c.getCreatedAt(), UserRef.of(c.getAuthor()), c.getAuthor().getRole()))
                .toList();
        var attachments = t.getAttachments().stream()
                .map(a -> new AttachmentView(a.getId(), a.getOriginalName(), a.getMimeType(), a.getSize(), a.getCreatedAt(), UserRef.of(a.getUploader())))
                .toList();
        var events = t.getEvents().stream()
                .map(e -> new EventView(e.getId(), e.getType(), e.getFromValue(), e.getToValue(), e.getCreatedAt(), UserRef.of(e.getActor())))
                .toList();
        var next = staff ? List.copyOf(fr.portfolio.helpdesk.sla.TicketWorkflow.nextStatuses(t.getStatus())) : List.<TicketStatus>of();
        return new TicketDetail(t.getId(), t.getTitle(), t.getDescription(), t.getCategory(), t.getStatus(), t.getPriority(),
                UserRef.of(t.getRequester()), UserRef.of(t.getAssignee()), t.getResponseDueAt(), t.getResolutionDueAt(),
                t.getFirstResponseAt(), t.getResolvedAt(), t.getClosedAt(), t.getCreatedAt(), sla(t, now), next,
                comments, attachments, events);
    }
}
