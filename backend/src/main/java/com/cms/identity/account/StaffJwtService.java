package com.cms.identity.account;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and validates JWTs for staff (ClinicAdmin/Doctor/Operations) sessions. Every
 * token carries a fixed {@code aud} claim of {@value #STAFF_AUDIENCE} - structurally
 * distinct from 002-patient-account-login's {@code PATIENT} audience, so a patient token
 * can never satisfy a staff-only check and vice versa (mirrors that feature's FR-008).
 * Signed with its own secret, separate from the patient-token signing key.
 */
@Component
public class StaffJwtService {

    public static final String STAFF_AUDIENCE = "staff";

    private static final Duration TOKEN_TTL = Duration.ofHours(12);
    private static final Logger log = LoggerFactory.getLogger(StaffJwtService.class);

    private final SecretKey signingKey;

    /**
     * _diagnostics [MAJOR] - [full-repo-audit] - [UNSAFE_JWT_DEFAULT]: see {@code
     * com.cms.patient.account.JwtService}'s identical fix - {@code staff.jwt.secret} no longer
     * falls back to a fixed, source-committed value; a blank secret generates a random signing
     * key for this run only instead.
     */
    public StaffJwtService(@Value("${staff.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn(
                    "staff.jwt.secret is not configured - generated a random signing key for this run only;"
                            + " existing staff tokens will not survive a restart. Set STAFF_JWT_SECRET before"
                            + " any real deployment.");
            this.signingKey = Jwts.SIG.HS256.key().build();
        } else {
            this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String issueToken(UUID accountId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(accountId.toString())
                .audience()
                .add(STAFF_AUDIENCE)
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_TTL)))
                .signWith(signingKey)
                .compact();
    }

    /** @return the authenticated account ID if the token is valid and carries the staff audience; empty otherwise. */
    public Optional<UUID> validateAndGetAccountId(String token) {
        try {
            Claims claims =
                    Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            if (claims.getAudience() == null || !claims.getAudience().contains(STAFF_AUDIENCE)) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
