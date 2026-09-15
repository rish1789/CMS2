package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.scheduling.Session;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 015 FR-001, spec US1 AC1: a Schedule with no existing Sessions gets exactly one Session per applicable date in the next 15 days. */
class SessionGenerationFirstRunTest extends AbstractSessionGenerationIntegrationTest {

    @Test
    void firstRunGeneratesOneSessionPerApplicableDateInTheHorizon() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveFixedTimeSchedule(clinic, doctor); // Mon/Wed/Fri
        LocalDate runDate = LocalDate.of(2026, 9, 3); // a Thursday

        int created = sessionGenerationService.generate(runDate);

        long expectedCount = runDate.datesUntil(runDate.plusDays(15))
                .filter(d -> Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY).contains(d.getDayOfWeek()))
                .count();
        assertThat(created).isEqualTo(expectedCount);

        List<Session> sessions = sessionRepository.findBySchedule_Id(schedule.getId());
        assertThat(sessions).hasSize((int) expectedCount);
        assertThat(sessions)
                .allSatisfy(s -> assertThat(Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
                        .contains(s.getSessionDate().getDayOfWeek()));
        assertThat(sessions).allSatisfy(s -> {
            assertThat(s.getSessionDate()).isAfterOrEqualTo(runDate).isBefore(runDate.plusDays(15));
        });
    }
}
