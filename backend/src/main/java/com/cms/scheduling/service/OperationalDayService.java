package com.cms.scheduling.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.stereotype.Service;

/**
 * 061-doctor-live-status (spec.md BR-011/BR-012): the single centralized function resolving
 * which "operational day" a given instant falls in - the clinic's day runs 04:30:00 (inclusive)
 * through 04:29:59.999 the next calendar date (exclusive of that next 04:30:00), not midnight.
 *
 * <p>Scoped to this feature's own calculations only (spec.md Clarifications, 2026-09-23, A3) -
 * no existing feature's date handling (nightly session generation, the no-show sweep, Day
 * Sheet's calendar-date grouping) is affected by this class.
 */
@Service
public class OperationalDayService {

    private static final LocalTime BOUNDARY = LocalTime.of(4, 30);

    /**
     * BR-011: an instant at or after 04:30 belongs to that calendar date's operational day; an
     * instant before 04:30 belongs to the previous calendar date's operational day.
     */
    public LocalDate operationalDateOf(LocalDateTime instant) {
        LocalDate calendarDate = instant.toLocalDate();
        if (instant.toLocalTime().isBefore(BOUNDARY)) {
            return calendarDate.minusDays(1);
        }
        return calendarDate;
    }
}
