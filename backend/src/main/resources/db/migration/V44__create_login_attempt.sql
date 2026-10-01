-- 075-login-hardening (D-3C-2): consecutive failed logins per identifier, for the 5-in-15-minutes
-- lockout. The identifier is stored only as a SHA-256 hash of its trimmed, lower-cased form, so an
-- email that was tried but never registered is not kept in plain text. One row per (realm,
-- identifier); a successful login deletes it.
CREATE TABLE login_attempt (
    realm              VARCHAR(16) NOT NULL,
    identifier_hash    CHAR(64)    NOT NULL,
    failed_count       INTEGER     NOT NULL CHECK (failed_count >= 0),
    window_started_at  TIMESTAMPTZ NOT NULL,
    locked_until       TIMESTAMPTZ,
    PRIMARY KEY (realm, identifier_hash)
);
