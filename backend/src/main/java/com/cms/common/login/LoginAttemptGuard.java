package com.cms.common.login;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 075-login-hardening (D-3C-2, OWASP Authentication Cheat Sheet / NIST SP 800-63B 5.2.2): locks an
 * identifier after repeated failed logins, keyed by realm and a hash of the normalised identifier
 * - never by whether an account exists, so the lockout cannot reveal which emails are registered.
 *
 * <p>Failures and successes are written in their own transaction, so a failed login is counted
 * even though the login request itself fails. The row is locked ({@code FOR UPDATE}) while it is
 * updated, so concurrent failures are never lost.
 */
@Component
public class LoginAttemptGuard {

    private final JdbcTemplate jdbcTemplate;
    private final LoginAttemptPolicy policy;
    private final Clock clock;

    @Autowired
    public LoginAttemptGuard(
            JdbcTemplate jdbcTemplate,
            @Value("${app.login.max-failures:5}") int maxFailures,
            @Value("${app.login.window-minutes:15}") long windowMinutes,
            @Value("${app.login.lock-minutes:15}") long lockMinutes) {
        this(
                jdbcTemplate,
                new LoginAttemptPolicy(maxFailures, Duration.ofMinutes(windowMinutes), Duration.ofMinutes(lockMinutes)),
                Clock.systemUTC());
    }

    LoginAttemptGuard(JdbcTemplate jdbcTemplate, LoginAttemptPolicy policy, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.policy = policy;
        this.clock = clock;
    }

    /** Refuses the attempt, before any password check, while the identifier is locked. */
    public void requireNotLocked(LoginRealm realm, String identifier) {
        Instant now = clock.instant();
        LoginAttemptState state = read(realm, identifierHash(realm, identifier), false);
        if (state.lockedAt(now)) {
            throw new LoginTemporarilyLockedException(state.retryAfterSeconds(now));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(LoginRealm realm, String identifier) {
        String hash = identifierHash(realm, identifier);
        Instant now = clock.instant();
        jdbcTemplate.update(
                "INSERT INTO login_attempt (realm, identifier_hash, failed_count, window_started_at, locked_until)"
                        + " VALUES (?, ?, 0, ?, NULL) ON CONFLICT (realm, identifier_hash) DO NOTHING",
                realm.name(), hash, Timestamp.from(now));
        LoginAttemptState current = read(realm, hash, true);
        LoginAttemptState next = (current.failedCount() == 0 ? LoginAttemptState.none() : current).afterFailure(now, policy);
        jdbcTemplate.update(
                "UPDATE login_attempt SET failed_count = ?, window_started_at = ?, locked_until = ?"
                        + " WHERE realm = ? AND identifier_hash = ?",
                next.failedCount(),
                Timestamp.from(next.windowStartedAt()),
                next.lockedUntil() == null ? null : Timestamp.from(next.lockedUntil()),
                realm.name(),
                hash);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(LoginRealm realm, String identifier) {
        jdbcTemplate.update(
                "DELETE FROM login_attempt WHERE realm = ? AND identifier_hash = ?",
                realm.name(), identifierHash(realm, identifier));
    }

    private LoginAttemptState read(LoginRealm realm, String hash, boolean forUpdate) {
        List<LoginAttemptState> rows = jdbcTemplate.query(
                "SELECT failed_count, window_started_at, locked_until FROM login_attempt"
                        + " WHERE realm = ? AND identifier_hash = ?" + (forUpdate ? " FOR UPDATE" : ""),
                (rs, i) -> new LoginAttemptState(
                        rs.getInt("failed_count"),
                        rs.getTimestamp("window_started_at").toInstant(),
                        rs.getTimestamp("locked_until") == null ? null : rs.getTimestamp("locked_until").toInstant()),
                realm.name(),
                hash);
        return rows.isEmpty() ? LoginAttemptState.none() : rows.get(0);
    }

    /** SHA-256 of the realm and the trimmed, lower-cased identifier, as 64 hex characters. */
    static String identifierHash(LoginRealm realm, String identifier) {
        String normalised = realm.name() + ":" + (identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalised.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
