package fr.portfolio.helpdesk.user;

import fr.portfolio.helpdesk.common.ApiException;
import fr.portfolio.helpdesk.domain.Role;
import fr.portfolio.helpdesk.domain.User;
import fr.portfolio.helpdesk.domain.UserRepository;
import fr.portfolio.helpdesk.security.AuthUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "users")
public class UserController {

    public record StaffView(Long id, String name, String email, Role role) {}

    public record CreateUserRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 2, max = 80) String name,
            @NotNull Role role,
            @NotBlank @Size(min = 8) @Pattern(regexp = "(?=.*[A-Z])(?=.*\\d).*", message = "doit contenir une majuscule et un chiffre") String password) {}

    public record UpdateUserRequest(
            @Size(min = 2, max = 80) String name,
            Role role,
            Boolean active,
            @Size(min = 8) @Pattern(regexp = "(?=.*[A-Z])(?=.*\\d).*", message = "doit contenir une majuscule et un chiffre") String password) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /** Liste des techniciens pour l'affectation des tickets. */
    @GetMapping("/staff")
    @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
    public List<StaffView> staff() {
        return users.findByActiveTrueAndRoleInOrderByNameAsc(EnumSet.of(Role.TECHNICIAN, Role.ADMIN)).stream()
                .map(u -> new StaffView(u.getId(), u.getName(), u.getEmail(), u.getRole())).toList();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserView> findAll() {
        return users.findAllByOrderByNameAsc().stream().map(UserView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserView create(@Valid @RequestBody CreateUserRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) throw ApiException.conflict("E-mail déjà utilisé");
        return UserView.of(users.save(new User(req.email(), req.name(), encoder.encode(req.password()), req.role())));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest req, @AuthenticationPrincipal AuthUser me) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
        if (id.equals(me.id()) && (Boolean.FALSE.equals(req.active()) || (req.role() != null && req.role() != Role.ADMIN))) {
            throw ApiException.badRequest("Vous ne pouvez pas retirer vos propres droits administrateur");
        }
        if (req.name() != null) u.setName(req.name());
        if (req.role() != null) u.setRole(req.role());
        if (req.active() != null) u.setActive(req.active());
        if (req.password() != null) u.setPasswordHash(encoder.encode(req.password()));
        return UserView.of(u);
    }

    /** Pour un utilisateur qui a perdu son téléphone. */
    @PostMapping("/{id}/reset-2fa")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserView resetTwoFactor(@PathVariable Long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
        u.setTwoFactorEnabled(false);
        u.setTwoFactorSecret(null);
        return UserView.of(u);
    }
}
