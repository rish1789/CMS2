package com.cms.scheduling.integration;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.Schedule;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.SessionGenerationService;
import com.cms.scheduling.SessionRepository;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 015: extends 013/014's own fixture directly to reuse its Clinic/DoctorProfile/staffing
 * builders, adding Session cleanup and Super Admin Basic-Auth (mirroring
 * {@code AbstractAdminIntegrationTest}'s pattern, since this feature's manual-trigger
 * endpoint sits behind that same chain, not the staff-JWT one).
 */
public abstract class AbstractSessionGenerationIntegrationTest extends AbstractScheduleIntegrationTest {

    protected static final String SUPER_ADMIN_USERNAME = "test-super-admin";
    protected static final String SUPER_ADMIN_PASSWORD = "Str0ng!Pass";

    @DynamicPropertySource
    static void superAdminProperties(DynamicPropertyRegistry registry) {
        registry.add("admin.super-admin.username", () -> SUPER_ADMIN_USERNAME);
        registry.add("admin.super-admin.password", () -> SUPER_ADMIN_PASSWORD);
    }

    @Autowired
    protected SessionRepository sessionRepository;

    @Autowired
    protected SessionGenerationService sessionGenerationService;

    @AfterEach
    void cleanSessions() {
        sessionRepository.deleteAll();
    }

    protected static String superAdminAuthHeader() {
        String credentials = SUPER_ADMIN_USERNAME + ":" + SUPER_ADMIN_PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /** A Fixed-Time Schedule (Mon/Wed/Fri, 9-13, 15-minute slots) for a doctor staffed at the given clinic. */
    protected Schedule saveFixedTimeSchedule(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = new Schedule(
                doctor,
                clinic,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
                LocalTime.of(9, 0),
                LocalTime.of(13, 0),
                ScheduleMode.FIXED_TIME,
                15);
        return scheduleRepository.save(schedule);
    }

    /** A Queue/Token Schedule (Tue/Thu, 14-17, no slot interval) for a doctor staffed at the given clinic. */
    protected Schedule saveQueueSchedule(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = new Schedule(
                doctor,
                clinic,
                Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                LocalTime.of(14, 0),
                LocalTime.of(17, 0),
                ScheduleMode.QUEUE,
                null);
        return scheduleRepository.save(schedule);
    }

    /** Every day of the week - guarantees at least one applicable date regardless of the run date used in a test. */
    protected Schedule saveEveryDaySchedule(Clinic clinic, DoctorProfile doctor, ScheduleMode mode, Integer slotIntervalMinutes) {
        Schedule schedule = new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class), LocalTime.of(9, 0), LocalTime.of(10, 0), mode, slotIntervalMinutes);
        return scheduleRepository.save(schedule);
    }
}
