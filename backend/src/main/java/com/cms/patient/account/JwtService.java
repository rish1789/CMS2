package com.cms.patient.account;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and validates JWTs for Patient Account sessions. Every token carries a fixed
 * {@code aud} claim of {@value #PATIENT_AUDIENCE} - a staff-only authorization check (any
 * future one, from 003/004) that requires a different audience will structurally reject a
 * patient token, and vice versa (FR-008 - implemented at the token level, not merely by
 * convention). Signed with its own secret, entirely separate from any staff-token signing
 * key (research.md's session-mechanism decision).
 */
@Component
public class JwtService {

    public static final String PATIENT_AUDIENCE = "patient";

    private static final Duration TOKEN_TTL = Duration.ofHours(12);
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;

    /**
     * _diagnostics [MAJOR] - [full-repo-audit] - [UNSAFE_JWT_DEFAULT]: {@code
     * application.yml}'s {@code patient.jwt.secret} used to fall back to a fixed,
     * source-committed string if {@code PATIENT_JWT_SECRET} was never set - unlike {@code
     * SuperAdminSecurityConfig}'s username/password (which fails safe: a random, unguessable
     * value generated per-run when unset), a forgotten env var here would silently sign every
     * real patient token with a secret sitting in public source control, letting anyone forge
     * one. Now mirrors that same fail-safe shape: a blank secret generates a random signing key
     * for this run only (tokens don't survive a restart - an acceptable dev-only cost) instead of
     * ever falling back to a known value.
     */
    public JwtService(@Value("${patient.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn(
                    "patient.jwt.secret is not configured - generated a random signing key for this run only;"
                            + " existing patient tokens will not survive a restart. Set PATIENT_JWT_SECRET"
                            + " before any real deployment.");
            this.signingKey = Jwts.SIG.HS256.key().build();
        } else {
            this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String issueToken(UUID patientAccountId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(patientAccountId.toString())
                .audience()
                .add(PATIENT_AUDIENCE)
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_TTL)))
                .signWith(signingKey)
                .compact();
    }

    /** @throws JwtException if the token is malformed, expired, or has an invalid signature. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    /** True only if the token parses and its {@code aud} claim is the patient audience (FR-008). */
    public boolean isPatientToken(String token) {
        try {
            Claims claims = parse(token);
            return claims.getAudience() != null && claims.getAudience().contains(PATIENT_AUDIENCE);
        } catch (JwtException e) {
            return false;
        }
    }
}
