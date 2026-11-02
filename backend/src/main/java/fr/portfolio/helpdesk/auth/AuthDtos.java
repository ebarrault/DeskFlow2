package fr.portfolio.helpdesk.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.portfolio.helpdesk.user.UserView;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 2, max = 80) String name,
            @NotBlank @Size(min = 8) @Pattern(regexp = "(?=.*[A-Z])(?=.*\\d).*", message = "doit contenir une majuscule et un chiffre") String password) {}

    public record CodeRequest(@NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    public record VerifyRequest(@NotBlank String tempToken, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

    /** Soit { requires2fa, tempToken }, soit { accessToken, user }. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(Boolean requires2fa, String tempToken, String accessToken, UserView user) {
        public static LoginResponse twoFactor(String tempToken) {
            return new LoginResponse(true, tempToken, null, null);
        }

        public static LoginResponse authenticated(String accessToken, UserView user) {
            return new LoginResponse(null, null, accessToken, user);
        }
    }

    public record TwoFactorStatus(boolean twoFactorEnabled) {}
}
