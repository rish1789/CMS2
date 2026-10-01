package com.cms.common.login;

import java.time.Duration;

/**
 * 075-login-hardening (D-3C-2): {@code maxFailures} consecutive failures inside {@code window}
 * (counted from the first of them) lock the identifier for {@code lockDuration}.
 */
public record LoginAttemptPolicy(int maxFailures, Duration window, Duration lockDuration) {}
