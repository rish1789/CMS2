package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.PatientAccount;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * Patient-facing doctors-at-a-clinic listing, backing {@code JoinWaitlistForm}'s doctor picker.
 * Mirrors {@code ClinicDoctorControllerTest}'s scenarios for the staff endpoint, but with no
 * clinic-membership requirement on the caller - a patient is never staffed anywhere.
 */
class PatientDoctorListingTest extends AbstractPatientBookingIntegrationTest {

    @Test
    void listsDoctorsStaffedAtTheClinic() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/doctors", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(patientAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(1))
                .andExpect(jsonPath("$.doctors[0].doctorProfileId").value(doctor.getId().toString()))
                .andExpect(jsonPath("$.doctors[0].specialization").value("General Medicine"))
                .andExpect(jsonPath("$.doctors[0].staffCode").doesNotExist());
    }

    @Test
    void excludesADeactivatedDoctorRoleAssignment() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorProfile();
        linkDoctorToClinic(doctor, clinic, false);
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/doctors", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(patientAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(0));
    }

    @Test
    void aPatientNotStaffedAnywhereCanStillListAClinicsDoctor() throws Exception {
        Clinic clinic = saveClinic();
        saveDoctorStaffedAt(clinic);
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/doctors", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(patientAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(1));
    }

    @Test
    void aSmallPageStillReportsTheFullTotalCount() throws Exception {
        Clinic clinic = saveClinic();
        saveDoctorStaffedAt(clinic);
        saveDoctorStaffedAt(clinic);
        saveDoctorStaffedAt(clinic);
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/doctors", clinic.getId())
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(patientAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(2))
                .andExpect(jsonPath("$.totalCount").value(3));
    }

    @Test
    void theSearchFilterMatchesByName() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile match = saveDoctorStaffedAt(clinic);
        saveDoctorStaffedAt(clinic);
        PatientAccount patientAccount = savePatientAccount();

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/doctors", clinic.getId())
                        .param("q", match.getAccount().getName())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(patientAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(1))
                .andExpect(jsonPath("$.doctors[0].doctorProfileId").value(match.getId().toString()));
    }

    @Test
    void noTokenIsRejected() throws Exception {
        Clinic clinic = saveClinic();

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/doctors", clinic.getId()))
                .andExpect(status().isUnauthorized());
    }
}
