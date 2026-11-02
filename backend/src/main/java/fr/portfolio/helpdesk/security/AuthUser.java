package fr.portfolio.helpdesk.security;

import fr.portfolio.helpdesk.domain.Role;
import fr.portfolio.helpdesk.domain.User;

/** Utilisateur authentifié, disponible via @AuthenticationPrincipal. */
public record AuthUser(Long id, String email, String name, Role role) {

    public static AuthUser of(User u) {
        return new AuthUser(u.getId(), u.getEmail(), u.getName(), u.getRole());
    }

    public boolean isStaff() {
        return role.isStaff();
    }
}
