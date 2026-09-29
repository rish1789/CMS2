package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.waitlist.domain.WaitlistEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 062-rejected-clinic-gating (FR-001/FR-002/FR-003, SC-001, tasks.md T010): with the clinic
 * rejected, every real booking endpoint - all five creation paths plus the waitlist claim that
 * books through the patient path - answers 409 CLINIC_NOT_ACCEPTING_APPOINTMENTS and writes no
 * booking. Staff paths use a ClinicAdmin caller: the only staff role still allowed through the
 * access gate at a rejected clinic, so the booking refusal itself is what's under test.
 */
class RejectedClinicBookingRefusalTest extends AbstractDeVerificationCascadeIntegrationTest {

    @Autowired
    private JwtService patientJwtService;

    private Clinic clinic;
    private DoctorProfile doctor;
    private Slot openSlot;
    private Session queueSession;
    private String adminToken;
    private PatientAccount patient;
    private long bookingsBefore;

    @BeforeEach
    void rejectedClinicWithBookableCapacity() {
        clinic = saveClinic();
        clinic.setVerified(false);
        clinic = clinicRepository.save(clinic);
        doctor = saveDoctorStaffedAt(clinic);
        Session fixed = saveFixedTimeSessionWithSlots(clinic, doctor);
        openSlot = sessionRepository.findBySchedule_Id(fixed.getSchedule().getId()).stream()
                .filter(s -> s.getSessionDate().equals(LocalDate.now().plusDays(1)))
                .flatMap(s -> slotRepository.findBySession_Id(s.getId()).stream())
                .min(Comparator.comparing(Slot::getStartTime))
                .orElseThrow();
        queueSession = saveQueueSession(clinic, doctor);
        adminToken = clinicAdminToken(clinic);
        patient = savePatientAccount();

        clinicVerificationService.reject(clinic.getId(), "DUPLICATE_REGISTRATION", null, SUPER_ADMIN_USERNAME);
        bookingsBefore = bookingRepository.count();
    }

    private void assertRefusedAndNothingBooked(ResultActions result) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CLINIC_NOT_ACCEPTING_APPOINTMENTS"));
        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);
    }

    private String patientBody() {
        return "{\"patientName\":\"Asha\",\"appointmentTypeId\":\"" + UUID.randomUUID() + "\"}";
    }

    private String staffBody() {
        return "{\"patientName\":\"Walk In\",\"appointmentTypeId\":\"" + UUID.randomUUID() + "\"}";
    }

    @Test
    void patientFixedTimeBookingIsRefused() throws Exception {
        assertRefusedAndNothingBooked(mockMvc.perform(
                post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), openSlot.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientJwtService.issueToken(patient.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody())));
    }

    @Test
    void patientQueueBookingIsRefused() throws Exception {
        assertRefusedAndNothingBooked(mockMvc.perform(
                post("/api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinic.getId(), queueSession.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientJwtService.issueToken(patient.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody())));
    }

    @Test
    void staffFixedTimeBookingIsRefused() throws Exception {
        assertRefusedAndNothingBooked(mockMvc.perform(
                post("/api/v1/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), openSlot.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffBody())));
    }

    @Test
    void staffQueueBookingIsRefused() throws Exception {
        assertRefusedAndNothingBooked(mockMvc.perform(
                post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinic.getId(), queueSession.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staffBody())));
    }

    /** 063-front-desk-walk-in: the walk-in path is now the clinic-level front-desk registration. */
    @Test
    void walkInIsRefused() throws Exception {
        assertRefusedAndNothingBooked(mockMvc.perform(post("/api/v1/clinics/{clinicId}/walk-ins", clinic.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"" + openSlot.getSession().getId() + "\",\"patientName\":\"Walk In\","
                        + "\"appointmentTypeId\":\"" + UUID.randomUUID() + "\",\"visitReason\":\"PAIN\"}")));
    }

    @Test
    void claimingAWaitlistOfferMadeBeforeRejectionIsRefused() throws Exception {
        // An offer outstanding at the moment of rejection. The rejection itself expires OFFERED
        // entries (FR-011), so this one is re-created as OFFERED afterwards to prove the booking
        // path underneath the claim refuses independently of the waitlist sweep.
        WaitlistEntry entry = saveWaitingEntry(clinic, doctor, patient);
        entry.offer(Instant.now(), openSlot);
        waitlistEntryRepository.save(entry);

        mockMvc.perform(post("/api/v1/patients/waitlist-entries/{entryId}/claim", entry.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientJwtService.issueToken(patient.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patientBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CLINIC_NOT_ACCEPTING_APPOINTMENTS"));
        assertThat(bookingRepository.count()).isEqualTo(bookingsBefore);
    }
}
