package fr.portfolio.helpdesk.ticket;

import fr.portfolio.helpdesk.domain.Priority;
import fr.portfolio.helpdesk.domain.TicketStatus;
import fr.portfolio.helpdesk.security.AuthUser;
import fr.portfolio.helpdesk.ticket.TicketDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/tickets")
@Tag(name = "tickets")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<TicketSummary> findAll(@AuthenticationPrincipal AuthUser user,
                                               @RequestParam(required = false) TicketStatus status,
                                               @RequestParam(required = false) Priority priority,
                                               @RequestParam(required = false) Long assigneeId,
                                               @RequestParam(required = false) Boolean unassigned,
                                               @RequestParam(required = false) Boolean slaBreached,
                                               @RequestParam(required = false) String search,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        return service.findAll(user, new TicketFilter(status, priority, assigneeId, unassigned, slaBreached, search), page, pageSize);
    }

    @GetMapping("/{id}")
    public TicketDetail findOne(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return service.findOne(user, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketSummary create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateTicketRequest req) {
        return service.create(user, req);
    }

    @PatchMapping("/{id}")
    public TicketSummary update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody UpdateTicketRequest req) {
        return service.update(user, id, req);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView comment(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @Valid @RequestBody CommentRequest req) {
        return service.addComment(user, id, req);
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentView upload(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return service.addAttachment(user, id, file);
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthUser user, @PathVariable Long id, @PathVariable Long attachmentId) {
        var d = service.download(user, id, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(d.name(), StandardCharsets.UTF_8).build().toString())
                .body(d.resource());
    }
}
