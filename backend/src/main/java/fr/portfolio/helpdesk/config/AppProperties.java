package fr.portfolio.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        List<String> corsOrigins,
        String uploadDir,
        String url,
        String mailFrom,
        boolean seed) {

    public record Jwt(String secret, Duration ttl) {}
}
