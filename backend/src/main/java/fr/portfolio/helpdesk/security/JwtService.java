package fr.portfolio.helpdesk.security;

import fr.portfolio.helpdesk.config.AppProperties;
import fr.portfolio.helpdesk.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    /** Jeton intermédiaire entre le mot de passe et le code 2FA. */
    public static final String PURPOSE_2FA = "2fa";

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(AppProperties props) {
        byte[] secret = props.jwt().secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret doit faire au moins 32 caractères");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.ttl = props.jwt().ttl();
    }

    public String accessToken(User user) {
        return build(user.getId(), null, ttl, user.getRole().name());
    }

    public String twoFactorToken(User user) {
        return build(user.getId(), PURPOSE_2FA, Duration.ofMinutes(5), null);
    }

    private String build(Long userId, String purpose, Duration validity, String role) {
        Instant now = Instant.now();
        var b = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)));
        if (purpose != null) b.claim("purpose", purpose);
        if (role != null) b.claim("role", role);
        return b.signWith(key).compact();
    }

    /** Claims d'un jeton valide (signature + expiration), vide sinon. */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
