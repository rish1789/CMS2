package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.exception.BookingLimitReachedException;
import com.cms.booking.service.PatientBookingService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.record.domain.Patient;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * 060-booking-abuse-prevention (spec.md FR-006, NFR-003): proves the booking-limit's row-lock
 * concurrency guard (research.md Decision 1) holds under two genuinely simultaneous booking
 * attempts from the same patient, already one below the default global cap of 15 - exactly one
 * must succeed (reaching the cap) and the other must be refused, never both. Written/compiled,
 * Docker-gated per this sandbox's standing Testcontainers limitation - mirrors
 * QueueBookingConcurrencyTest's own established shape.
 */
class BookingLimitConcurrencyTest extends AbstractPatientBookingIntegrationTest {

    @Test
    void exactlyOneOfTwoSimultaneousAttemptsSucceedsOnceThePatientIsOneBelowTheCap() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        AppointmentType appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        PatientAccount patientAccount = savePatientAccount();

        // Pre-seed 14 existing active bookings for this patient (across their own slots), one
        // below the default global cap of 15 - the default because no ProtectionSetting row is
        // configured in this test.
        List<Slot> allSlots = slotRepository.findBySession_Id(session.getId());
        Patient patient = patientRepository.save(new Patient(clinic, patientAccount, "Cap Test Patient", null));
        for (int i = 0; i < 14; i++) {
            Slot slot = allSlots.get(i);
            bookingRepository.save(Booking.bookedByPatient(
                    slot, patient, appointmentType, new BigDecimal("300.00"), patientAccount.getId()));
            slot.setStatus(com.cms.scheduling.domain.SlotStatus.BOOKED);
            slotRepository.save(slot);
        }

        Slot raceSlotA = allSlots.get(14);
        Slot raceSlotB = allSlots.get(15);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = List.of(
                    () -> attemptBooking(patientAccount.getId(), clinic.getId(), raceSlotA.getId(), appointmentType.getId()),
                    () -> attemptBooking(patientAccount.getId(), clinic.getId(), raceSlotB.getId(), appointmentType.getId()));

            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            long successCount = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).filter(Boolean::booleanValue).count();

            assertThat(successCount).isEqualTo(1);
            assertThat(bookingRepository.countByPatient_PatientAccount_IdAndStatus(patientAccount.getId(), BookingStatus.ACTIVE))
                    .isEqualTo(15);
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
        } catch (BookingLimitReachedException e) {
            return false;
        }
    }
}
