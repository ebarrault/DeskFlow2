package fr.portfolio.helpdesk.auth;

import fr.portfolio.helpdesk.auth.AuthDtos.LoginResponse;
import fr.portfolio.helpdesk.common.ApiException;
import fr.portfolio.helpdesk.domain.Role;
import fr.portfolio.helpdesk.domain.User;
import fr.portfolio.helpdesk.domain.UserRepository;
import fr.portfolio.helpdesk.security.JwtService;
import fr.portfolio.helpdesk.security.LoginAttemptService;
import fr.portfolio.helpdesk.user.UserView;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TotpService totp;
    private final LoginAttemptService attempts;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, TotpService totp, LoginAttemptService attempts) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.totp = totp;
        this.attempts = attempts;
    }

    @Transactional
    public LoginResponse register(AuthDtos.RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) throw ApiException.conflict("Un compte existe déjà avec cet e-mail");
        User u = users.save(new User(req.email(), req.name(), encoder.encode(req.password()), Role.USER));
        return LoginResponse.authenticated(jwt.accessToken(u), UserView.of(u));
    }

    /** Étape 1 : mot de passe. Si la 2FA est active, renvoie un jeton temporaire de 5 minutes. */
    public LoginResponse login(String email, String password) {
        if (attempts.isBlocked(email)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives, réessayez dans une minute");
        }
        User u = users.findByEmailIgnoreCase(email)
                .filter(User::isActive)
                .filter(x -> encoder.matches(password, x.getPasswordHash()))
                .orElseThrow(() -> {
                    attempts.failed(email);
                    return ApiException.unauthorized("Identifiants invalides");
                });
        attempts.succeeded(email);
        if (u.isTwoFactorEnabled()) return LoginResponse.twoFactor(jwt.twoFactorToken(u));
        return LoginResponse.authenticated(jwt.accessToken(u), UserView.of(u));
    }

    /** Étape 2 : code TOTP. */
    public LoginResponse verifyTwoFactor(String tempToken, String code) {
        var claims = jwt.parse(tempToken)
                .filter(c -> JwtService.PURPOSE_2FA.equals(c.get("purpose")))
                .orElseThrow(() -> ApiException.unauthorized("Session de connexion expirée, recommencez"));
        String key = "2fa:" + claims.getSubject();
        if (attempts.isBlocked(key)) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives");
        User u = users.findById(Long.valueOf(claims.getSubject()))
                .filter(x -> x.isActive() && x.isTwoFactorEnabled())
                .orElseThrow(() -> ApiException.unauthorized("Compte indisponible"));
        if (!totp.isValid(u.getTwoFactorSecret(), code)) {
            attempts.failed(key);
            throw ApiException.unauthorized("Code de vérification invalide");
        }
        attempts.succeeded(key);
        return LoginResponse.authenticated(jwt.accessToken(u), UserView.of(u));
    }

    @Transactional
    public TotpService.Enrollment setupTwoFactor(Long userId) {
        User u = get(userId);
        if (u.isTwoFactorEnabled()) throw ApiException.badRequest("La double authentification est déjà active");
        TotpService.Enrollment e = totp.enroll(u.getEmail());
        u.setTwoFactorSecret(e.secret()); // active seulement après confirmation d'un code
        return e;
    }

    @Transactional
    public AuthDtos.TwoFactorStatus enableTwoFactor(Long userId, String code) {
        User u = get(userId);
        if (u.getTwoFactorSecret() == null) throw ApiException.badRequest("Lancez d'abord la configuration");
        if (!totp.isValid(u.getTwoFactorSecret(), code)) throw ApiException.badRequest("Code invalide");
        u.setTwoFactorEnabled(true);
        return new AuthDtos.TwoFactorStatus(true);
    }

    @Transactional
    public AuthDtos.TwoFactorStatus disableTwoFactor(Long userId, String code) {
        User u = get(userId);
        if (!u.isTwoFactorEnabled()) throw ApiException.badRequest("La double authentification n'est pas active");
        if (!totp.isValid(u.getTwoFactorSecret(), code)) throw ApiException.badRequest("Code invalide");
        u.setTwoFactorEnabled(false);
        u.setTwoFactorSecret(null);
        return new AuthDtos.TwoFactorStatus(false);
    }

    private User get(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("Utilisateur introuvable"));
    }
}
