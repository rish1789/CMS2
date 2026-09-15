package com.cms.scheduling;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 015 FR-006: runs the identical logic {@link SessionGenerationController} exposes for manual re-trigger (FR-007). */
@Component
public class NightlySessionGenerationTrigger {

    private static final Logger log = LoggerFactory.getLogger(NightlySessionGenerationTrigger.class);

    private final SessionGenerationService sessionGenerationService;

    public NightlySessionGenerationTrigger(SessionGenerationService sessionGenerationService) {
        this.sessionGenerationService = sessionGenerationService;
    }

    /** 2:00 AM server time - an implementation default with no business significance to the specific hour (spec Assumptions). */
    @Scheduled(cron = "0 0 2 * * *")
    public void runNightlyGeneration() {
        LocalDate runDate = LocalDate.now();
        int sessionsCreated = sessionGenerationService.generate(runDate);
        log.info("Nightly session generation for {}: {} sessions created", runDate, sessionsCreated);
    }
}
