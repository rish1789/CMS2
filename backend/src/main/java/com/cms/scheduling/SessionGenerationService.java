package com.cms.scheduling;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 015: reads every existing {@link Schedule} ("active" currently means "exists" - 009 has
 * no deactivation mechanism, spec Assumptions) and materializes {@link Session} rows for
 * every applicable date in the 15-day horizon starting at the run date, via {@link
 * ScheduleSessionGenerator}. 018 adds Slot pre-generation for Fixed-Time Sessions directly
 * into that per-schedule step, in the same transaction as the Session's own creation -
 * never for Queue/Token (013, unbuilt at the time, owns that mode's separate on-demand path).
 */
@Service
public class SessionGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SessionGenerationService.class);

    private final ScheduleRepository scheduleRepository;
    private final ScheduleSessionGenerator scheduleSessionGenerator;

    public SessionGenerationService(
            ScheduleRepository scheduleRepository, ScheduleSessionGenerator scheduleSessionGenerator) {
        this.scheduleRepository = scheduleRepository;
        this.scheduleSessionGenerator = scheduleSessionGenerator;
    }

    /**
     * Not itself {@code @Transactional}: each Schedule is generated in its own transaction
     * inside {@link ScheduleSessionGenerator#generateForSchedule}, so one Schedule's rare
     * race/failure never rolls back another Schedule's already-committed Sessions in the
     * same run (research.md). Delegates to a genuinely separate bean rather than calling a
     * {@code @Transactional} method on itself - a same-class self-invocation would silently
     * bypass Spring's transactional proxy entirely (the same bug class this codebase already
     * hit twice before, 020/022 - found live here on 2026-09-08, see {@link
     * ScheduleSessionGenerator}'s own Javadoc for how this one went undetected until then).
     */
    public int generate(LocalDate runDate) {
        int totalCreated = 0;
        for (Schedule schedule : scheduleRepository.findAll()) {
            try {
                totalCreated += scheduleSessionGenerator.generateForSchedule(schedule.getId(), runDate);
            } catch (RuntimeException e) {
                // Convergence fix: a failure generating for one Schedule (e.g. the rare
                // true-race case research.md documents) must never prevent attempting
                // the remaining Schedules in the same run.
                log.error("Session generation failed for schedule {} on run date {}", schedule.getId(), runDate, e);
            }
        }
        return totalCreated;
    }
}
