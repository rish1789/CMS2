package com.cms.scheduling;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Extracted from {@link SessionGenerationService} (bug found live while verifying
 * 041-staff-console-pickers - never caught by any test in this session, since every prior
 * test run has been blocked by the sandbox's Testcontainers/Docker limitation): {@code
 * generateForSchedule} used to live on {@code SessionGenerationService} itself and was
 * called via plain self-invocation from {@code generate()}'s loop - {@code @Transactional}
 * on a self-invoked method is silently never applied (bypasses the Spring AOP proxy
 * entirely), the same self-invocation bug class this codebase already hit twice before
 * (020, 022). With no active transaction, {@code schedule.getDaysOfWeek()} (a lazy {@code
 * @ElementCollection}) threw {@code LazyInitializationException} on every single call, in
 * any real (non-mocked) environment - confirmed live, `sessionsCreated` was silently 0 for
 * every schedule, always, since 011 shipped.
 *
 * <p>Being a genuinely separate bean makes the cross-bean call from {@code
 * SessionGenerationService.generate()} go through the real proxy, so {@code @Transactional}
 * actually applies. Takes a {@code scheduleId} (not a {@code Schedule} reference) and
 * re-fetches it inside this method's own transaction - the {@code Schedule} instance
 * {@code generate()}'s {@code findAll()} loop holds is itself already detached (loaded
 * outside any transaction), so passing it through would still fail even with a real
 * transaction wrapping the call.
 */
@Service
public class ScheduleSessionGenerator {

    private static final int HORIZON_DAYS = 15;

    private final ScheduleRepository scheduleRepository;
    private final SessionRepository sessionRepository;
    private final SlotGenerationService slotGenerationService;

    public ScheduleSessionGenerator(
            ScheduleRepository scheduleRepository,
            SessionRepository sessionRepository,
            SlotGenerationService slotGenerationService) {
        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
        this.slotGenerationService = slotGenerationService;
    }

    @Transactional
    public int generateForSchedule(UUID scheduleId, LocalDate runDate) {
        Schedule schedule = scheduleRepository
                .findById(scheduleId)
                .orElseThrow(() -> new IllegalStateException("No Schedule with id " + scheduleId));

        List<LocalDate> applicableDates = runDate.datesUntil(runDate.plusDays(HORIZON_DAYS))
                .filter(date -> schedule.getDaysOfWeek().contains(date.getDayOfWeek()))
                .toList();

        if (applicableDates.isEmpty()) {
            return 0;
        }

        Set<LocalDate> alreadyGenerated = sessionRepository
                .findBySchedule_IdAndSessionDateIn(schedule.getId(), applicableDates)
                .stream()
                .map(Session::getSessionDate)
                .collect(Collectors.toSet());

        int created = 0;
        for (LocalDate date : applicableDates) {
            if (alreadyGenerated.contains(date)) {
                continue;
            }
            Session session = new Session(
                    schedule,
                    schedule.getClinic(),
                    schedule.getDoctorProfile(),
                    date,
                    schedule.getMode(),
                    schedule.getStartTime(),
                    schedule.getEndTime(),
                    schedule.getSlotIntervalMinutes());
            Session saved = sessionRepository.save(session);
            if (saved.getMode() == ScheduleMode.FIXED_TIME) {
                slotGenerationService.generateSlotsFor(saved);
            }
            created++;
        }
        return created;
    }
}
