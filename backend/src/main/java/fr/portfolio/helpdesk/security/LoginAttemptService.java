package fr.portfolio.helpdesk.security;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Limite les tentatives de connexion : 5 échecs par minute et par identifiant. */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String key) {
        Deque<Instant> q = failures.get(key.toLowerCase());
        if (q == null) return false;
        synchronized (q) {
            purge(q);
            return q.size() >= MAX_ATTEMPTS;
        }
    }

    public void failed(String key) {
        Deque<Instant> q = failures.computeIfAbsent(key.toLowerCase(), k -> new ArrayDeque<>());
        synchronized (q) {
            purge(q);
            q.addLast(clock.instant());
        }
    }

    public void succeeded(String key) {
        failures.remove(key.toLowerCase());
    }

    private void purge(Deque<Instant> q) {
        Instant limit = clock.instant().minus(WINDOW);
        while (!q.isEmpty() && q.peekFirst().isBefore(limit)) q.pollFirst();
    }
}
