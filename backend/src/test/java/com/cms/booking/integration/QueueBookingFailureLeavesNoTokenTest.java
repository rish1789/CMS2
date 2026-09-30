package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.service.PatientQueueBookingService;
import com.cms.booking.service.QueuePositionService;
import com.cms.booking.service.StaffQueueBookingService;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.record.domain.Patient;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 067 US4 (FR-008, SC-007): a queue booking that fails after its token was reserved leaves no token
 * behind - the next booking takes that number and queue positions count only real bookings. The
 * failure is forced in the database itself: a test-only trigger rejects the booking INSERT for one
 * patient, exactly as a genuine insert failure would, on both queue booking paths.
 */
class QueueBookingFailureLeavesNoTokenTest extends AbstractQueueBookingIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private QueuePositionService queuePositionService;

    @AfterEach
    void dropTrigger() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS test_067_reject_booking ON booking");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS test_067_reject_booking()");
    }

    @Test
    void aFailedPatientQueueBookingLeavesNoTokenAndTheNextBookingReusesItsNumber() {
        Clinic clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveQueueSessionOn(clinic, doctor, LocalDate.now().plusDays(1));
        AppointmentType type = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        PatientAccount first = savePatientAccount();
        PatientAccount doomed = savePatientAccount();
        PatientAccount next = savePatientAccount();
        // Linked in advance, so the booking path reuses this Patient and the trigger can target it.
        Patient doomedPatient = patientRepository.save(new Patient(clinic, doomed, "Doomed Patient", null));

        Booking firstBooking = patientBook(first, clinic, session, type);
        rejectBookingsFor(doomedPatient.getId());
        assertThatThrownBy(() -> patientBook(doomed, clinic, session, type)).isInstanceOf(RuntimeException.class);
        Booking nextBooking = patientBook(next, clinic, session, type);

        assertNoOrphanAndNumberReused(session, firstBooking, nextBooking);
    }

    @Test
    void aFailedStaffQueueBookingLeavesNoTokenAndTheNextBookingReusesItsNumber() {
        Clinic clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveQueueSessionOn(clinic, doctor, LocalDate.now().plusDays(1));
        AppointmentType type = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        UUID staff = clinicAdminAccountId(clinic);
        Patient doomedPatient = saveExistingPatient(clinic);

        Booking firstBooking = staffBook(staff, clinic, session, type, saveExistingPatient(clinic));
        rejectBookingsFor(doomedPatient.getId());
        assertThatThrownBy(() -> staffBook(staff, clinic, session, type, doomedPatient)).isInstanceOf(RuntimeException.class);
        Booking nextBooking = staffBook(staff, clinic, session, type, saveExistingPatient(clinic));

        assertNoOrphanAndNumberReused(session, firstBooking, nextBooking);
    }

    private void assertNoOrphanAndNumberReused(Session session, Booking firstBooking, Booking nextBooking) {
        assertThat(firstBooking.getSlot().getTokenNumber()).isEqualTo(1);
        // The failed booking's number (2) is not burned.
        assertThat(nextBooking.getSlot().getTokenNumber()).isEqualTo(2);

        List<Integer> tokens = slotRepository.findBySession_Id(session.getId()).stream()
                .map(Slot::getTokenNumber)
                .filter(Objects::nonNull)
                .toList();
        List<Integer> bookedTokens = bookingRepository.findAll().stream()
                .filter(b -> b.getSlot().getSession().getId().equals(session.getId()))
                .map(b -> b.getSlot().getTokenNumber())
                .toList();
        assertThat(tokens).as("every token has a booking").containsExactlyInAnyOrderElementsOf(bookedTokens);

        // Only the one real waiting patient is ahead.
        assertThat(queuePositionService.positionOf(nextBooking).position()).isEqualTo(2);
    }

    private void rejectBookingsFor(UUID patientId) {
        jdbcTemplate.execute("CREATE OR REPLACE FUNCTION test_067_reject_booking() RETURNS trigger AS $$ BEGIN "
                + "IF NEW.patient_id = '" + patientId + "'::uuid THEN RAISE EXCEPTION 'test 067: forced booking failure'; END IF; "
                + "RETURN NEW; END $$ LANGUAGE plpgsql");
        jdbcTemplate.execute("CREATE TRIGGER test_067_reject_booking BEFORE INSERT ON booking "
                + "FOR EACH ROW EXECUTE FUNCTION test_067_reject_booking()");
    }

    private Booking patientBook(PatientAccount account, Clinic clinic, Session session, AppointmentType type) {
        return patientQueueBookingService.bookSlot(account.getId(), clinic.getId(), session.getId(),
                new PatientQueueBookingService.BookSlotInput("Patient " + account.getId(), type.getId()));
    }

    private Booking staffBook(UUID staff, Clinic clinic, Session session, AppointmentType type, Patient patient) {
        return staffQueueBookingService.bookSlot(staff, clinic.getId(), session.getId(),
                new StaffQueueBookingService.BookSlotInput(patient.getId(), null, null, type.getId()));
    }

    private UUID clinicAdminAccountId(Clinic clinic) {
        String unique = UUID.randomUUID().toString();
        Account admin = accountRepository.save(new Account(
                "Admin X", "adminx-" + unique + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "CAX-" + unique.substring(0, 13), null));
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        return admin.getId();
    }
}
