package com.cms.scheduling.config;

import com.cms.scheduling.service.NightlySessionGenerationTrigger;


import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 015 FR-006: enables {@code @Scheduled} for {@link NightlySessionGenerationTrigger} - and with it
 * every other {@code @Scheduled} job in the app (no-show, auto-completion and waitlist-expiry
 * sweeps, retention purges, flag detection); this is the only {@code @EnableScheduling}.
 *
 * <p>{@code cms.scheduling.enabled=false} turns all of them off. Only the test resources set it, so
 * the sweeps cannot change integration-test fixtures mid-test (tests call the sweep services
 * directly). Never set it in a deployed environment: no-shows, auto-completion, offer expiry and
 * nightly session generation would all silently stop.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "cms.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SessionGenerationSchedulingConfig {}
