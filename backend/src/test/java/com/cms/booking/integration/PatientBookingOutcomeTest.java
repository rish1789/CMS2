package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.record.domain.Patient;
import com.cms.scheduling.domain.Schedule;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 069-patient-visit-outcomes (live-audit findings 1-3): the patient's own visit outcome and
 * cancellation eligibility on the list, the new detail endpoint and live status, against a real
 * database. Synthetic data only. Times of day are chosen so nothing depends on when the suite runs:
 * outcomes depend only on the date, and the time-sensitive cutoff is covered by the unit test.
 */
class PatientBookingOutcomeTest extends AbstractPatientBookingIntegrationTest {

    private Clinic clinic;
    private DoctorProfile doctor;
    private AppointmentType type;
    private PatientAccount account;
    private String token;

    @BeforeEach
    void patientWithADoctor() {
        clinic = saveClinic();
        doctor = saveDoctorStaffedAt(clinic);
        type = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        account = savePatientAccount();
        token = "Bearer " + patientToken(account);
    }

    private Session fixedSession(LocalDate date) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class), LocalTime.of(0, 0), LocalTime.of(23, 0), ScheduleMode.FIXED_TIME, 15));
        return sessionRepository.save(new Session(
                schedule, clinic, doctor, date, ScheduleMode.FIXED_TIME, LocalTime.of(0, 0), LocalTime.of(23, 0), 15));
    }

    private Booking book(Session session, LocalTime start, SlotStatus slotStatus, PatientAccount owner) {
        Slot slot = new Slot(session, start, start.plusMinutes(15));
        slot.setStatus(slotStatus);
        slot = slotRepository.save(slot);
        // One Patient record per (clinic, account) - 009 FR-005a's unique index.
        Patient patient = patientRepository.findByClinic_IdAndPatientAccount_Id(clinic.getId(), owner.getId())
                .orElseGet(() -> patientRepository.save(new Patient(clinic, owner, "Synthetic Patient", null)));
        return bookingRepository.save(Booking.bookedByPatient(slot, patient, type, new BigDecimal("300.00"), owner.getId()));
    }

    private Booking book(LocalDate date, SlotStatus slotStatus) {
        return book(fixedSession(date), LocalTime.of(0, 0), slotStatus, account);
    }

    @Test
    void theListCarriesEachBookingsOwnOutcomeAndEligibility() throws Exception {
        Booking noShowToday = book(LocalDate.now(), SlotStatus.NO_SHOW);
        Booking future = book(LocalDate.now().plusDays(5), SlotStatus.BOOKED);
        Booking pastUnresolved = book(LocalDate.now().minusDays(1), SlotStatus.BOOKED);
        Booking cancelled = book(LocalDate.now().plusDays(3), SlotStatus.OPEN);
        cancelled.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(cancelled);

        mockMvc.perform(get("/api/v1/patients/bookings").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings[?(@.id=='%s')].visitOutcome", noShowToday.getId()).value("NO_SHOW"))
                .andExpect(jsonPath("$.bookings[?(@.id=='%s')].cancellation.reason", noShowToday.getId())
                        .value("VISIT_RESOLVED"))
                .andExpect(jsonPath("$.bookings[?(@.id=='%s')].visitOutcome", future.getId()).value("SCHEDULED"))
                .andExpect(jsonPath("$.bookings[?(@.id=='%s')].cancellation.allowed", future.getId()).value(true))
                .andExpect(jsonPath("$.bookings[?(@.id=='%s')].visitOutcome", pastUnresolved.getId()).value("NOT_RECORDED"))
                .andExpect(jsonPath("$.bookings[?(@.id=='%s')].visitOutcome", cancelled.getId()).value("CANCELLED"))
                .andExpect(jsonPath("$.bookings[?(@.id=='%s')].cancellation.reason", cancelled.getId())
                        .value("ALREADY_CANCELLED"));
    }

    /** The delayed rule: a booking today that is still Booked stays scheduled, whatever the clock says. */
    @Test
    void aBookingTodayThatIsStillBookedIsScheduled() throws Exception {
        Booking today = book(LocalDate.now(), SlotStatus.BOOKED);

        mockMvc.perform(get("/api/v1/patients/bookings/{id}", today.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitOutcome").value("SCHEDULED"))
                .andExpect(jsonPath("$.cancellation.reason").value("CUTOFF_PASSED"));
    }

    @Test
    void theDetailEndpointIsOwnerOnly() throws Exception {
        Booking mine = book(LocalDate.now().plusDays(5), SlotStatus.BOOKED);
        String otherPatient = "Bearer " + patientToken(savePatientAccount());

        mockMvc.perform(get("/api/v1/patients/bookings/{id}", mine.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mine.getId().toString()))
                .andExpect(jsonPath("$.visitOutcome").value("SCHEDULED"))
                .andExpect(jsonPath("$.cancellation.allowed").value(true));
        mockMvc.perform(get("/api/v1/patients/bookings/{id}", mine.getId()).header(HttpHeaders.AUTHORIZATION, otherPatient))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/patients/bookings/{id}", mine.getId())).andExpect(status().isUnauthorized());
    }

    /** Finding 1: a resolved session must not make the patient's own no-show read "Visit complete". */
    @Test
    void liveStatusShowsTheOwnNoShowNotVisitComplete() throws Exception {
        Session session = fixedSession(LocalDate.now());
        Booking missed = book(session, LocalTime.of(0, 0), SlotStatus.NO_SHOW, account);
        book(session, LocalTime.of(0, 15), SlotStatus.COMPLETED, savePatientAccount());

        mockMvc.perform(get("/api/v1/patients/bookings/{id}/live-status", missed.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitOutcome").value("NO_SHOW"))
                .andExpect(jsonPath("$.statusText").value("Missed appointment"))
                .andExpect(jsonPath("$.statusText").value(Matchers.not("Visit complete")));
    }

    @Test
    void liveStatusSaysVisitCompleteOnlyForTheOwnCompletedVisit() throws Exception {
        Session session = fixedSession(LocalDate.now());
        Booking done = book(session, LocalTime.of(0, 0), SlotStatus.COMPLETED, account);

        mockMvc.perform(get("/api/v1/patients/bookings/{id}/live-status", done.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitOutcome").value("COMPLETED"))
                .andExpect(jsonPath("$.statusText").value("Visit complete"));
    }

    /** FR-005: displayed eligibility is advisory - the endpoint keeps its own checks and errors. */
    @Test
    void theCancelEndpointStillRefusesOnItsOwn() throws Exception {
        Booking past = book(LocalDate.now().minusDays(1), SlotStatus.BOOKED);

        mockMvc.perform(post("/api/v1/patients/bookings/{id}/cancel", past.getId())
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SCHEDULE_CONFLICT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CANCELLATION_CUTOFF_PASSED"));
    }
}
