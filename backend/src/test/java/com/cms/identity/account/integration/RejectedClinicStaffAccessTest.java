package com.cms.identity.account.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.config.StaffJwtService;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.staff.integration.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 062-rejected-clinic-gating (FR-007, User Story 4, SC-006, tasks.md T025): at a rejected clinic
 * only the ClinicAdmin keeps staff access - at sign-in, on every clinic-scoped request (including a
 * token issued before the rejection), and in the clinic picker. Restore gives access back.
 */
class RejectedClinicStaffAccessTest extends AbstractStaffIntegrationTest {

    @Autowired
    private StaffJwtService staffJwtService;

    private Clinic clinic;
    private Account doctor;
    private Account admin;
    private String doctorTokenFromBeforeRejection;

    @BeforeEach
    void rejectedClinicWithADoctorAndAnAdmin() {
        clinic = saveClinic("Rejected Clinic");
        doctor = saveAccount("doctor.rejected@example.com", "Str0ng!Pass", "DR-7001");
        admin = saveAccount("admin.rejected@example.com", "Str0ng!Pass", "CA-7001");
        roleAssignmentRepository.save(new RoleAssignment(doctor, clinic, RoleAssignment.Role.Doctor));
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        doctorTokenFromBeforeRejection = staffJwtService.issueToken(doctor.getId());

        clinic.reject(Clinic.RejectionReason.DUPLICATE_REGISTRATION, null, "super-admin");
        clinic = clinicRepository.save(clinic);
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/staff/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + email + "\",\"password\":\"Str0ng!Pass\"}"));
    }

    @Test
    void theDoctorIsRefusedAtSignIn() throws Exception {
        login("doctor.rejected@example.com")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CLINIC_NOT_ACTIVE"));
    }

    @Test
    void theDoctorsTokenFromBeforeTheRejectionIsRefusedOnClinicPages() throws Exception {
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorTokenFromBeforeRejection))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CLINIC_NOT_ACTIVE"));
    }

    @Test
    void theClinicAdminSignsInSeesTheClinicAndCanViewItsPages() throws Exception {
        login("admin.rejected@example.com").andExpect(status().isOk());
        String adminToken = staffJwtService.issueToken(admin.getId());

        mockMvc.perform(get("/api/v1/clinics/mine").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinics.length()").value(1))
                .andExpect(jsonPath("$.totalCount").value(1));
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void theDoctorsClinicPickerOmitsTheRejectedClinic() throws Exception {
        mockMvc.perform(get("/api/v1/clinics/mine")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorTokenFromBeforeRejection))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinics.length()").value(0))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void aDoctorWhoAlsoWorksAtANormalClinicSignsInAndKeepsThatClinicOnly() throws Exception {
        Clinic normal = saveClinic("Normal Clinic");
        roleAssignmentRepository.save(new RoleAssignment(doctor, normal, RoleAssignment.Role.Doctor));

        login("doctor.rejected@example.com").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors", normal.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorTokenFromBeforeRejection))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorTokenFromBeforeRejection))
                .andExpect(status().isForbidden());
    }

    @Test
    void restoringTheClinicGivesTheDoctorAccessBack() throws Exception {
        clinic.restore();
        clinicRepository.save(clinic);

        login("doctor.rejected@example.com").andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorTokenFromBeforeRejection))
                .andExpect(status().isOk());
    }
}
