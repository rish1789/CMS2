package com.cms.waitlist.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.PatientAccount;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 031 US2 AC3 (T014): staff joining a patient onto the waitlist on their behalf. */
class StaffWaitlistJoinTest extends AbstractWaitlistIntegrationTest {

    @Test
    void staffJoinProducesTheIdenticalEntryShapeAsPatientJoin() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/waitlist", clinic.getId())
                        .header("Authorization", "Bearer " + clinicAdminToken(clinic))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientAccountId\":\"" + patientAccount.getId() + "\",\"doctorProfileId\":\""
                                + doctor.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.clinicId").value(clinic.getId().toString()))
                .andExpect(jsonPath("$.doctorProfileId").value(doctor.getId().toString()));
    }

    @Test
    void rejectsAnUnknownPatientAccount() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/waitlist", clinic.getId())
                        .header("Authorization", "Bearer " + clinicAdminToken(clinic))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientAccountId\":\"" + UUID.randomUUID() + "\",\"doctorProfileId\":\""
                                + doctor.getId() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PATIENT_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void rejectsStaffWithZeroRoleAtTheClinic() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/waitlist", clinic.getId())
                        .header("Authorization", "Bearer " + unrelatedStaffToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientAccountId\":\"" + patientAccount.getId() + "\",\"doctorProfileId\":\""
                                + doctor.getId() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void rejectsTheDoctorsOwnToken() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/waitlist", clinic.getId())
                        .header("Authorization", "Bearer " + doctorToken(doctor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientAccountId\":\"" + patientAccount.getId() + "\",\"doctorProfileId\":\""
                                + doctor.getId() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
