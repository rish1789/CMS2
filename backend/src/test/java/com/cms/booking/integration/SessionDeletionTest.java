package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.waitlist.domain.WaitlistEntry;
import com.cms.waitlist.repository.WaitlistEntryRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * real-bug-fix 2026-09-17: a Schedule edit never rewrites Sessions already generated from the
 * old, wrong values (ScheduleService's own non-retroactivity invariant) - this is the intended
 * way to remove a stale one so the next generation run rebuilds it from the corrected Schedule.
 */
class SessionDeletionTest extends AbstractSessionCancellationIntegrationTest {

    // The repository's @Modifying update runs inside a service transaction in production;
    // a test calling it directly must supply one.
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private WaitlistEntryRepository waitlistEntryRepository;

    @Test
    void deletesASessionThatWasNeverUsed() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(delete("/api/v1/clinics/{clinicId}/sessions/{sessionId}", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(sessionRepository.findById(session.getId())).isEmpty();
        assertThat(slotRepository.findBySession_Id(session.getId())).isEmpty();
    }

    @Test
    void blocksDeletionWhenASlotHasEverHadABooking() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        bookSlot(clinic, doctor, slot);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(delete("/api/v1/clinics/{clinicId}/sessions/{sessionId}", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SESSION_DELETION_BLOCKED"));

        assertThat(sessionRepository.findById(session.getId())).isPresent();
    }

    @Test
    void blocksDeletionWhenASlotHasEverHadAWaitlistOffer() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount patientAccount = savePatientAccount();
        WaitlistEntry entry = waitlistEntryRepository.save(new WaitlistEntry(clinic, patientAccount, doctor, null));
        transactionTemplate.executeWithoutResult(status -> waitlistEntryRepository.offerIfWaiting(
                entry.getId(), Instant.now(), Instant.now().plusSeconds(1800), slot));
        String token = clinicAdminToken(clinic);

        mockMvc.perform(delete("/api/v1/clinics/{clinicId}/sessions/{sessionId}", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SESSION_DELETION_BLOCKED"));

        assertThat(sessionRepository.findById(session.getId())).isPresent();
        waitlistEntryRepository.delete(entry);
    }

    @Test
    void theDoctorThemselvesIsForbidden() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        String token = doctorToken(doctor);

        mockMvc.perform(delete("/api/v1/clinics/{clinicId}/sessions/{sessionId}", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void anUnknownSessionIsNotFound() throws Exception {
        Clinic clinic = saveClinic();
        String token = clinicAdminToken(clinic);

        mockMvc.perform(delete("/api/v1/clinics/{clinicId}/sessions/{sessionId}", clinic.getId(), UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
    }
}
