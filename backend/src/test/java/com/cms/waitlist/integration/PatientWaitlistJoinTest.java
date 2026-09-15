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

/** 031 US2 AC1/AC2 (T012) and error paths (T013). */
class PatientWaitlistJoinTest extends AbstractWaitlistIntegrationTest {

    @Test
    void joinsForASpecificDoctor() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/waitlist", clinic.getId())
                        .header("Authorization", "Bearer " + patientToken(patientAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorProfileId\":\"" + doctor.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.doctorProfileId").value(doctor.getId().toString()))
                .andExpect(jsonPath("$.specialization").doesNotExist());
    }

    @Test
    void joinsForASpecializationWithNoDoctorPreference() throws Exception {
        Clinic clinic = saveClinic();
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/waitlist", clinic.getId())
                        .header("Authorization", "Bearer " + patientToken(patientAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specialization\":\"Cardiology\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.specialization").value("Cardiology"))
                .andExpect(jsonPath("$.doctorProfileId").doesNotExist());
    }

    @Test
    void rejectsNeitherDoctorNorSpecialization() throws Exception {
        Clinic clinic = saveClinic();
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/waitlist", clinic.getId())
                        .header("Authorization", "Bearer " + patientToken(patientAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("WAITLIST_TARGET_REQUIRED"));
    }

    @Test
    void rejectsADoctorNotStaffedAtTheClinic() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile elsewhere = saveDoctorStaffedAt(saveClinic(), "Cardiology");
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/waitlist", clinic.getId())
                        .header("Authorization", "Bearer " + patientToken(patientAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"doctorProfileId\":\"" + elsewhere.getId() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DOCTOR_NOT_STAFFED_AT_CLINIC"));
    }

    @Test
    void rejectsAnUnknownClinic() throws Exception {
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/waitlist", UUID.randomUUID())
                        .header("Authorization", "Bearer " + patientToken(patientAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specialization\":\"Cardiology\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CLINIC_NOT_FOUND"));
    }
}
