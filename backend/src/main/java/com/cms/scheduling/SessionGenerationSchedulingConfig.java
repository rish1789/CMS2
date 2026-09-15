package com.cms.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 015 FR-006: enables {@code @Scheduled} for {@link NightlySessionGenerationTrigger} - not registered anywhere else in this codebase yet. */
@Configuration
@EnableScheduling
public class SessionGenerationSchedulingConfig {}
