package com.cms.common.login;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 075-login-hardening FR-003/FR-004 (D-3C-2): the lockout arithmetic - 5 consecutive failures
 * inside a 15-minute window lock the identifier for 15 minutes. Pure value logic, fixed instants.
 */
class LoginAttemptStateTest {

    private static final LoginAttemptPolicy POLICY = new LoginAttemptPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(15));
    private static final Instant T0 = Instant.parse("2026-10-01T06:00:00Z");

    private static LoginAttemptState failTimes(int times, Instant at) {
        LoginAttemptState state = LoginAttemptState.none();
        for (int i = 0; i < times; i++) {
            state = state.afterFailure(at, POLICY);
        }
        return state;
    }

    @Test
    void fourFailuresDoNotLock() {
        LoginAttemptState state = failTimes(4, T0);
        assertThat(state.failedCount()).isEqualTo(4);
        assertThat(state.lockedAt(T0)).isFalse();
    }

    @Test
    void theFifthFailureLocksForFifteenMinutes() {
        LoginAttemptState state = failTimes(5, T0);
        assertThat(state.lockedAt(T0)).isTrue();
        assertThat(state.lockedAt(T0.plus(Duration.ofMinutes(15)).minusSeconds(1))).isTrue();
        assertThat(state.lockedAt(T0.plus(Duration.ofMinutes(15)))).isFalse();
        assertThat(state.retryAfterSeconds(T0.plusSeconds(60))).isEqualTo(14 * 60);
    }

    @Test
    void failuresSpreadBeyondTheWindowNeverAddUpToALock() {
        LoginAttemptState state = LoginAttemptState.none();
        for (int i = 0; i < 10; i++) {
            state = state.afterFailure(T0.plus(Duration.ofMinutes(16L * i)), POLICY);
        }
        assertThat(state.failedCount()).isEqualTo(1);
        assertThat(state.lockedAt(T0.plus(Duration.ofMinutes(16L * 9)))).isFalse();
    }

    @Test
    void afterALockExpiresTheCountStartsAgain() {
        LoginAttemptState locked = failTimes(5, T0);
        Instant later = T0.plus(Duration.ofMinutes(16));

        LoginAttemptState next = locked.afterFailure(later, POLICY);

        assertThat(next.failedCount()).isEqualTo(1);
        assertThat(next.lockedAt(later)).isFalse();
    }

    @Test
    void identifiersAreNormalisedBeforeHashingAndRealmsAreSeparate() {
        String a = LoginAttemptGuard.identifierHash(LoginRealm.STAFF, "  Asha@Example.com ");
        String b = LoginAttemptGuard.identifierHash(LoginRealm.STAFF, "asha@example.com");
        assertThat(a).isEqualTo(b).hasSize(64).doesNotContain("asha");
        assertThat(LoginAttemptGuard.identifierHash(LoginRealm.PATIENT, "asha@example.com")).isNotEqualTo(a);
    }
}
