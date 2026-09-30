package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.BookingAttemptLog;
import com.cms.booking.domain.BookingAttemptOutcome;
import com.cms.booking.exception.RateLimitedException;
import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.booking.service.PatientBookingService;
import com.cms.booking.service.PatientQueueBookingService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.scheduling.domain.Schedule;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 060-booking-abuse-prevention (spec.md FR-007, FR-008; research.md Decision 1): a burst of
 * simultaneous attempts from the same patient (default 8 attempts/10-minute window, no
 * ProtectionSetting row configured in this test) admits exactly the threshold, on both booking
 * paths: each admitted attempt's row is written under the patient row lock and visible to the
 * next attempt's count. The burst is larger than the test connection pool (10), which also proves
 * no attempt needs a second connection while it holds that lock.
 */
class BookingRateLimitConcurrencyTest extends AbstractPatientBookingIntegrationTest {

    private static final int THRESHOLD = 8;
    private static final int ATTEMPTS = 12;

    @Autowired
    private PatientQueueBookingService patientQueueBookingService;

    @Autowired
    private BookingAttemptLogRepository attemptLogRepository;

    @Test
    void aBurstOfSimultaneousFixedTimeAttemptsAdmitsExactlyTheThreshold() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        AppointmentType appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        PatientAccount patientAccount = savePatientAccount();
        List<Slot> allSlots = slotRepository.findBySession_Id(session.getId());

        List<Outcome> outcomes = runConcurrently(i -> () -> patientBookingService.bookSlot(
                patientAccount.getId(), clinic.getId(), allSlots.get(i).getId(),
                new PatientBookingService.BookSlotInput("Race Patient", appointmentType.getId())));

        assertThat(outcomes).filteredOn(o -> o == Outcome.BOOKED).hasSize(THRESHOLD);
        assertThat(outcomes).filteredOn(o -> o == Outcome.RATE_LIMITED).hasSize(ATTEMPTS - THRESHOLD);
        assertThat(loggedOutcomes(patientAccount))
                .containsExactlyInAnyOrderElementsOf(expectedLog(THRESHOLD, BookingAttemptOutcome.SUCCESS));
    }

    @Test
    void aBurstOfSimultaneousQueueAttemptsAdmitsExactlyTheThreshold() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveQueueSessionTomorrow(clinic, doctor);
        AppointmentType appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        PatientAccount patientAccount = savePatientAccount();

        List<Outcome> outcomes = runConcurrently(i -> () -> patientQueueBookingService.bookSlot(
                patientAccount.getId(), clinic.getId(), session.getId(),
                new PatientQueueBookingService.BookSlotInput("Race Patient", appointmentType.getId())));

        // Token issuance itself may lose a race (PB-003, tracked separately); every admitted
        // attempt counts toward the window whatever its outcome, so the rate-limited count is exact.
        assertThat(outcomes).filteredOn(o -> o == Outcome.RATE_LIMITED).hasSize(ATTEMPTS - THRESHOLD);
        assertThat(loggedOutcomes(patientAccount))
                .filteredOn(o -> o != BookingAttemptOutcome.RATE_LIMITED)
                .hasSize(THRESHOLD);
    }

    @Test
    void aFixedTimeAttemptThatFailsAfterTheGateStillCountsTowardTheWindow() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        AppointmentType appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        PatientAccount patientAccount = savePatientAccount();
        Slot slot = anOpenSlotOf(session);
        PatientBookingService.BookSlotInput input =
                new PatientBookingService.BookSlotInput("Repeat Patient", appointmentType.getId());

        patientBookingService.bookSlot(patientAccount.getId(), clinic.getId(), slot.getId(), input);
        // Same slot again: refused below the protection gate, and the booking transaction rolls back.
        assertThatThrownBy(() -> patientBookingService.bookSlot(
                        patientAccount.getId(), clinic.getId(), slot.getId(), input))
                .isNotInstanceOf(RateLimitedException.class);

        // FR-008: the rolled-back attempt is still on the log.
        assertThat(loggedOutcomes(patientAccount))
                .containsExactlyInAnyOrder(BookingAttemptOutcome.SUCCESS, BookingAttemptOutcome.OTHER_FAILURE);
    }

    private enum Outcome {
        BOOKED,
        RATE_LIMITED,
        OTHER_FAILURE
    }

    private List<Outcome> runConcurrently(java.util.function.IntFunction<Runnable> attempt) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(ATTEMPTS);
        try {
            List<Callable<Outcome>> tasks = IntStream.range(0, ATTEMPTS)
                    .<Callable<Outcome>>mapToObj(i -> () -> {
                        try {
                            attempt.apply(i).run();
                            return Outcome.BOOKED;
                        } catch (RateLimitedException e) {
                            return Outcome.RATE_LIMITED;
                        } catch (RuntimeException e) {
                            return Outcome.OTHER_FAILURE;
                        }
                    })
                    .toList();
            List<Outcome> outcomes = new java.util.ArrayList<>();
            for (Future<Outcome> future : executor.invokeAll(tasks)) {
                outcomes.add(future.get());
            }
            return outcomes;
        } finally {
            executor.shutdown();
        }
    }

    private List<BookingAttemptOutcome> loggedOutcomes(PatientAccount patientAccount) {
        return attemptLogRepository.findAll().stream()
                .filter(log -> log.getPatientAccount().getId().equals(patientAccount.getId()))
                .map(BookingAttemptLog::getOutcome)
                .toList();
    }

    private List<BookingAttemptOutcome> expectedLog(int admitted, BookingAttemptOutcome admittedOutcome) {
        return IntStream.range(0, ATTEMPTS)
                .mapToObj(i -> i < admitted ? admittedOutcome : BookingAttemptOutcome.RATE_LIMITED)
                .toList();
    }

    /** Dated tomorrow so the session is accepting whatever time of day the suite runs. */
    private Session saveQueueSessionTomorrow(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.QUEUE, null));
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        sessionGenerationService.generate(tomorrow);
        return sessionRepository.findBySchedule_Id(schedule.getId()).stream()
                .filter(s -> s.getSessionDate().equals(tomorrow))
                .findFirst()
                .orElseThrow();
    }
}
