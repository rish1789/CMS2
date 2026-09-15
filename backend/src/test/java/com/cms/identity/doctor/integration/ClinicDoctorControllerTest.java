package com.cms.identity.doctor.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.Account;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.StaffJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** 041-staff-console-pickers T032/US4: doctors staffed at a clinic, regardless of license-verification status. */
@AutoConfigureMockMvc
class ClinicDoctorControllerTest extends AbstractDoctorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffJwtService staffJwtService;

    private String clinicAdminToken(Clinic clinic) {
        Account admin = accountRepository.save(new Account(
                "Admin", "admin@example.com", passwordEncoder.encode("Str0ng!Pass"), "CA-0001", null));
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        return staffJwtService.issueToken(admin.getId());
    }

    @Test
    void listsDoctorsStaffedAtTheClinicRegardlessOfVerificationStatus() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic", true);
        DoctorProfile verified = saveDoctorProfile("LIC-001", true, true);
        DoctorProfile unverified = saveDoctorProfile("LIC-002", false, true);
        linkDoctorToClinic(verified, clinic, true);
        linkDoctorToClinic(unverified, clinic, true);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(2));
    }

    @Test
    void excludesADeactivatedDoctorRoleAssignment() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic", true);
        DoctorProfile doctor = saveDoctorProfile("LIC-003", true, true);
        linkDoctorToClinic(doctor, clinic, false);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(0));
    }

    @Test
    void rejectsACallerWithNoActiveRoleAtTheClinic() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic", true);
        Clinic other = saveClinic("Other Clinic", true);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(other)))
                .andExpect(status().isForbidden());
    }

    /** pagination-unification-2026-09-10: a small page reports the full totalCount, not just this page's size. */
    @Test
    void aSmallPageStillReportsTheFullTotalCount() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic", true);
        linkDoctorToClinic(saveDoctorProfile("LIC-101", true, true), clinic, true);
        linkDoctorToClinic(saveDoctorProfile("LIC-102", true, true), clinic, true);
        linkDoctorToClinic(saveDoctorProfile("LIC-103", true, true), clinic, true);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors", clinic.getId())
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(2))
                .andExpect(jsonPath("$.totalCount").value(3));
    }

    /**
     * doctors-search-2026-09-10: the search filter matches by staff code (also name/specialization,
     * same query). Regression coverage for the null-searchPattern-in-CONCAT bug already fixed once
     * on the Roster's identical pattern - every other test in this file omits {@code q} entirely,
     * so a reintroduced CONCAT would fail every one of THOSE requests, not this one.
     */
    @Test
    void theSearchFilterMatchesByStaffCode() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic", true);
        linkDoctorToClinic(saveDoctorProfile("LIC-777", true, true), clinic, true);
        linkDoctorToClinic(saveDoctorProfile("LIC-888", true, true), clinic, true);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors", clinic.getId())
                        .param("q", "777")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(1))
                .andExpect(jsonPath("$.doctors[0].staffCode").value("DR-LIC-777"));
    }
}
