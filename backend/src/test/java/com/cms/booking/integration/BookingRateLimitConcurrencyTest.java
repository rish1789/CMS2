package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.exception.RateLimitedException;
import com.cms.booking.service.PatientBookingService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 060-booking-abuse-prevention (spec.md FR-006, NFR-003; research.md Decision 1): a burst of
 * simultaneous attempts from the same patient around the rate-limit threshold boundary (default
 * 8 attempts/10-minute window, no ProtectionSetting row configured in this test) must never
 * exceed it by more than the documented bounded race margin (at most one extra attempt beyond
 * the threshold, never an unbounded bypass). Written/compiled, Docker-gated per this sandbox's
 * standing Testcontainers limitation.
 */
class BookingRateLimitConcurrencyTest extends AbstractPatientBookingIntegrationTest {

    @Test
    void aBurstOfSimultaneousAttemptsNeverExceedsTheThresholdByMoreThanTheBoundedRaceMargin() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        AppointmentType appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        PatientAccount patientAccount = savePatientAccount();
        List<Slot> allSlots = slotRepository.findBySession_Id(session.getId());

        int attempts = 12;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, attempts)
                    .<Callable<Boolean>>mapToObj(i -> () -> attemptBooking(
                            patientAccount.getId(), clinic.getId(), allSlots.get(i).getId(), appointmentType.getId()))
                    .toList();

            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            long successCount = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).filter(Boolean::booleanValue).count();

            // Default threshold is 8; research.md Decision 1's documented race margin allows at
            // most one extra attempt to slip through, never an unbounded bypass.
            assertThat(successCount).isLessThanOrEqualTo(9);
            assertThat(successCount).isLessThan(attempts);
        } finally {
            executor.shutdown();
        }
    }

    private boolean attemptBooking(java.util.UUID patientAccountId, java.util.UUID clinicId, java.util.UUID slotId, java.util.UUID appointmentTypeId) {
        try {
            patientBookingService.bookSlot(
                    patientAccountId, clinicId, slotId,
                    new PatientBookingService.BookSlotInput("Race Patient", appointmentTypeId));
            return true;
        } catch (RateLimitedException e) {
            return false;
        }
    }
}
