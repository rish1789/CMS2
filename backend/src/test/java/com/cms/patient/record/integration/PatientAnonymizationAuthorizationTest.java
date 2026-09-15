package com.cms.patient.record.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.record.Patient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 037 US1 (T013): only Operations/ClinicAdmin may anonymize - a Doctor's own token is rejected; an unknown patient is rejected. */
class PatientAnonymizationAuthorizationTest extends AbstractPatientAnonymizationIntegrationTest {

    @Test
    void rejectsADoctorsOwnTokenWithNoOperationsOrClinicAdminRole() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Patient patient = savePatient(clinic, null);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/patients/{patientId}/anonymize", clinic.getId(), patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void allowsAnOperationsToken() throws Exception {
        Clinic clinic = saveClinic();
        Patient patient = savePatient(clinic, null);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/patients/{patientId}/anonymize", clinic.getId(), patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + operationsToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymized").value(true));
    }

    @Test
    void rejectsAnUnknownPatient() throws Exception {
        Clinic clinic = saveClinic();

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/patients/{patientId}/anonymize", clinic.getId(), UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PATIENT_NOT_FOUND"));
    }
}
