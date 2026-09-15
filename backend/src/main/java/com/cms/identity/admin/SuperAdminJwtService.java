package com.cms.identity.admin;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Issues and validates JWTs for the Super Admin session (040-super-admin-rbac-login),
 * replacing the prior per-request HTTP Basic Auth. Every token carries a fixed {@code
 * aud} claim of {@value #SUPER_ADMIN_AUDIENCE} - structurally distinct from staff's
 * {@code staff} and patient's {@code PATIENT} audiences, so neither of those tokens can
 * ever satisfy a Super Admin check. Signed with its own secret, separate from both other
 * signing keys. The subject is the configured Super Admin username, not a database row
 * ID - no such row exists (002-super-admin-clinic-verification).
 */
@Component
public class SuperAdminJwtService {

    public static final String SUPER_ADMIN_AUDIENCE = "SUPER_ADMIN";

    private static final Duration TOKEN_TTL = Duration.ofHours(12);
    private static final Logger log = LoggerFactory.getLogger(SuperAdminJwtService.class);

    private final SecretKey signingKey;

    /**
     * _diagnostics [MAJOR] - [full-repo-audit] - [UNSAFE_JWT_DEFAULT]: see {@code
     * com.cms.patient.account.JwtService}'s identical fix - {@code admin.super-admin.jwt.secret}
     * no longer falls back to a fixed, source-committed value; a blank secret generates a random
     * signing key for this run only instead, matching the fail-safe treatment {@code
     * SuperAdminSecurityConfig} already gives the username/password pair right above this class.
     */
    public SuperAdminJwtService(@Value("${admin.super-admin.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn(
                    "admin.super-admin.jwt.secret is not configured - generated a random signing key for this"
                            + " run only; existing Super Admin tokens will not survive a restart. Set"
                            + " SUPER_ADMIN_JWT_SECRET before any real deployment.");
            this.signingKey = Jwts.SIG.HS256.key().build();
        } else {
            this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String issueToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .audience()
                .add(SUPER_ADMIN_AUDIENCE)
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_TTL)))
                .signWith(signingKey)
                .compact();
    }

    /** @return the authenticated Super Admin username if the token is valid and carries the Super Admin audience; empty otherwise. */
    public Optional<String> validateAndGetUsername(String token) {
        try {
            Claims claims =
                    Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
            if (claims.getAudience() == null || !claims.getAudience().contains(SUPER_ADMIN_AUDIENCE)) {
                return Optional.empty();
            }
            return Optional.of(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
