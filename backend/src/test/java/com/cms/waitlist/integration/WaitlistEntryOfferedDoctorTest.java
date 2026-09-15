package com.cms.waitlist.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.PatientAccount;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * _diagnostics [HIGH] - [WAITLIST_CLAIM] - [RAW_ID_ENTRY]: {@code WaitlistEntryResponse
 * .offeredDoctorProfileId} - the *matched* doctor for an OFFERED entry, letting ClaimOfferCard
 * fetch that doctor's appointment types instead of asking the patient to type an Appointment
 * Type ID. Distinct from {@code doctorProfileId}, which is the originally-requested doctor and
 * is null for a specialization-only join even once matched.
 */
class WaitlistEntryOfferedDoctorTest extends AbstractWaitlistIntegrationTest {

    @Test
    void exposesTheMatchedDoctorForAnOfferedEntryJoinedBySpecialization() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount patientAccount = savePatientAccount();
        var entry = saveOfferedWaitlistEntry(
                clinic, patientAccount, null, "Cardiology", slot, Instant.now(), Instant.now().plusSeconds(1800));

        mockMvc.perform(get("/api/v1/patients/waitlist-entries")
                        .header("Authorization", "Bearer " + patientToken(patientAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(entry.getId().toString()))
                .andExpect(jsonPath("$[0].doctorProfileId").doesNotExist())
                .andExpect(jsonPath("$[0].offeredDoctorProfileId").value(doctor.getId().toString()));
    }

    @Test
    void leavesTheMatchedDoctorNullForAStillWaitingEntry() throws Exception {
        Clinic clinic = saveClinic();
        PatientAccount patientAccount = savePatientAccount();
        saveWaitlistEntry(clinic, patientAccount, null, "Cardiology", Instant.now());

        mockMvc.perform(get("/api/v1/patients/waitlist-entries")
                        .header("Authorization", "Bearer " + patientToken(patientAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].offeredDoctorProfileId").doesNotExist());
    }
}
