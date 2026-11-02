package fr.portfolio.helpdesk.user;

import fr.portfolio.helpdesk.domain.Role;
import fr.portfolio.helpdesk.domain.User;

import java.time.Instant;

/** Représentation publique : jamais de hash ni de secret 2FA. */
public record UserView(Long id, String email, String name, Role role, boolean active, boolean twoFactorEnabled, Instant createdAt) {
    public static UserView of(User u) {
        return new UserView(u.getId(), u.getEmail(), u.getName(), u.getRole(), u.isActive(), u.isTwoFactorEnabled(), u.getCreatedAt());
    }
}
