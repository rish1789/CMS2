package com.cms.identity.admin.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** T005: GET /api/v1/admin/doctors?verified={true|false} returns only profiles matching the requested state (FR-004). */
class PendingDoctorsListTest extends AbstractAdminIntegrationTest {

    @Test
    void unverifiedFilterReturnsOnlyUnverifiedDoctorsWithProfileDetails() throws Exception {
        var pending = saveDoctorProfile("pending.doc@example.com", "LIC-001", "ENT", false);
        saveDoctorProfile("verified.doc@example.com", "LIC-002", "Radiology", true);

        mockMvc.perform(get("/api/v1/admin/doctors")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(1))
                .andExpect(jsonPath("$.doctors[0].doctorProfileId").value(pending.getId().toString()))
                .andExpect(jsonPath("$.doctors[0].specialization").value("ENT"))
                .andExpect(jsonPath("$.doctors[0].licenseNumber").value("LIC-001"))
                .andExpect(jsonPath("$.doctors[0].experienceYears").value(5))
                .andExpect(jsonPath("$.doctors[0].licenseVerified").value(false))
                .andExpect(jsonPath("$.doctors[0].visible").value(true));
    }

    @Test
    void verifiedFilterReturnsOnlyVerifiedDoctors() throws Exception {
        saveDoctorProfile("pending.doc@example.com", "LIC-001", "ENT", false);
        var verified = saveDoctorProfile("verified.doc@example.com", "LIC-002", "Radiology", true);

        mockMvc.perform(get("/api/v1/admin/doctors")
                        .param("status", "VERIFIED")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(1))
                .andExpect(jsonPath("$.doctors[0].doctorProfileId").value(verified.getId().toString()));
    }

    /** pagination-unification-2026-09-10: a small page reports the full totalCount, not just this page's size - the platform-wide queue grows unbounded as more doctors onboard. */
    @Test
    void aSmallPageStillReportsTheFullTotalCount() throws Exception {
        saveDoctorProfile("pending.a@example.com", "LIC-101", "ENT", false);
        saveDoctorProfile("pending.b@example.com", "LIC-102", "ENT", false);
        saveDoctorProfile("pending.c@example.com", "LIC-103", "ENT", false);

        mockMvc.perform(get("/api/v1/admin/doctors")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(2))
                .andExpect(jsonPath("$.totalCount").value(3));
    }
}
