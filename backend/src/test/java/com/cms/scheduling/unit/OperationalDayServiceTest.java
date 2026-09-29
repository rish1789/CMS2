package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.scheduling.service.OperationalDayService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 061-doctor-live-status (spec.md BR-011/BR-012, User Story 3): the single centralized 04:30 AM
 * operational-day boundary function. Extended by T028 (061 tasks.md) with the month/year
 * rollover case.
 */
class OperationalDayServiceTest {

    private final OperationalDayService service = new OperationalDayService();

    @Test
    void justBeforeTheBoundaryBelongsToThePreviousCalendarDate() {
        LocalDateTime instant = LocalDateTime.of(2026, 9, 23, 4, 29, 59);

        assertThat(service.operationalDateOf(instant)).isEqualTo(LocalDate.of(2026, 9, 22));
    }

    @Test
    void exactlyAtTheBoundaryBelongsToTheCurrentCalendarDate() {
        LocalDateTime instant = LocalDateTime.of(2026, 9, 23, 4, 30, 0);

        assertThat(service.operationalDateOf(instant)).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    void justAfterTheBoundaryBelongsToTheCurrentCalendarDate() {
        LocalDateTime instant = LocalDateTime.of(2026, 9, 23, 4, 31, 0);

        assertThat(service.operationalDateOf(instant)).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    void wellBeforeTheBoundaryBelongsToThePreviousCalendarDate() {
        LocalDateTime instant = LocalDateTime.of(2026, 9, 23, 1, 0, 0);

        assertThat(service.operationalDateOf(instant)).isEqualTo(LocalDate.of(2026, 9, 22));
    }

    @Test
    void wellAfterTheBoundaryBelongsToTheCurrentCalendarDate() {
        LocalDateTime instant = LocalDateTime.of(2026, 9, 23, 14, 0, 0);

        assertThat(service.operationalDateOf(instant)).isEqualTo(LocalDate.of(2026, 9, 23));
    }

    @Test
    void justBeforeTheBoundaryOnNewYearsDayRollsBackIntoThePreviousYear() {
        LocalDateTime instant = LocalDateTime.of(2026, 1, 1, 4, 29, 59);

        assertThat(service.operationalDateOf(instant)).isEqualTo(LocalDate.of(2025, 12, 31));
    }

    @Test
    void exactlyAtTheBoundaryOnNewYearsDayBelongsToTheNewYear() {
        LocalDateTime instant = LocalDateTime.of(2026, 1, 1, 4, 30, 0);

        assertThat(service.operationalDateOf(instant)).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void justBeforeTheBoundaryOnTheLastDayOfTheYearBelongsToTheDayBefore() {
        LocalDateTime instant = LocalDateTime.of(2026, 12, 31, 4, 29, 0);

        assertThat(service.operationalDateOf(instant)).isEqualTo(LocalDate.of(2026, 12, 30));
    }

    @Test
    void justBeforeTheBoundaryOnTheFirstOfAMonthRollsBackIntoThePreviousMonth() {
        LocalDateTime instant = LocalDateTime.of(2026, 3, 1, 4, 0, 0);

        assertThat(service.operationalDateOf(instant)).isEqualTo(LocalDate.of(2026, 2, 28));
    }
}
