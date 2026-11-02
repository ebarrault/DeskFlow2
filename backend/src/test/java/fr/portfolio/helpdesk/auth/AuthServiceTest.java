package fr.portfolio.helpdesk.auth;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import fr.portfolio.helpdesk.common.ApiException;
import fr.portfolio.helpdesk.config.AppProperties;
import fr.portfolio.helpdesk.domain.Role;
import fr.portfolio.helpdesk.domain.User;
import fr.portfolio.helpdesk.domain.UserRepository;
import fr.portfolio.helpdesk.security.JwtService;
import fr.portfolio.helpdesk.security.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final UserRepository users = mock(UserRepository.class);
    final JwtService jwt = new JwtService(new AppProperties(
            new AppProperties.Jwt("secret-de-test-suffisamment-long-pour-hs256", Duration.ofHours(1)),
            List.of(), "./target/uploads", "http://localhost", "test@test", false));
    AuthService service;
    User alice;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, encoder, jwt, new TotpService(), new LoginAttemptService());
        alice = new User("alice@test.local", "Alice", encoder.encode("Secret123"), Role.USER);
        alice.setId(1L);
        when(users.findByEmailIgnoreCase(anyString())).thenAnswer(i -> Optional.of(alice).filter(u -> u.getEmail().equalsIgnoreCase(i.getArgument(0))));
        when(users.findById(anyLong())).thenReturn(Optional.of(alice));
    }

    private static String currentCode(String secret) throws Exception {
        return new DefaultCodeGenerator().generate(secret, Math.floorDiv(Instant.now().getEpochSecond(), 30));
    }

    @Test
    @DisplayName("mauvais mot de passe ou compte désactivé refusés")
    void rejectsBadCredentials() {
        assertThatThrownBy(() -> service.login("alice@test.local", "faux")).isInstanceOf(ApiException.class);
        alice.setActive(false);
        assertThatThrownBy(() -> service.login("alice@test.local", "Secret123")).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("bloque après 5 échecs consécutifs")
    void bruteForce() {
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login("alice@test.local", "faux")).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> service.login("alice@test.local", "Secret123"))
                .extracting(e -> ((ApiException) e).getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("sans 2FA : jeton d'accès directement")
    void loginWithout2fa() {
        var r = service.login("ALICE@test.local", "Secret123");
        assertThat(r.accessToken()).isNotBlank();
        assertThat(r.requires2fa()).isNull();
        assertThat(r.user().email()).isEqualTo("alice@test.local");
    }

    @Test
    @DisplayName("parcours complet de la double authentification")
    void fullTwoFactorFlow() throws Exception {
        var enrollment = service.setupTwoFactor(1L);
        assertThat(enrollment.qrCode()).startsWith("data:image/png;base64,");
        assertThat(enrollment.otpauthUrl()).startsWith("otpauth://totp/");

        service.enableTwoFactor(1L, currentCode(enrollment.secret()));
        assertThat(alice.isTwoFactorEnabled()).isTrue();

        var step1 = service.login("alice@test.local", "Secret123");
        assertThat(step1.requires2fa()).isTrue();
        assertThat(step1.accessToken()).isNull();

        assertThatThrownBy(() -> service.verifyTwoFactor(step1.tempToken(), "000000")).isInstanceOf(ApiException.class);

        var step2 = service.verifyTwoFactor(step1.tempToken(), currentCode(enrollment.secret()));
        assertThat(step2.accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("un jeton d'accès classique ne vaut pas jeton 2FA")
    void accessTokenIsNotTwoFactorToken() {
        String access = jwt.accessToken(alice);
        assertThatThrownBy(() -> service.verifyTwoFactor(access, "123456"))
                .hasMessageContaining("expirée");
    }
}
