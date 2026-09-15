package com.cms.identity.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** T007: editing to a license number already used by a different profile is rejected 409, no changes (FR-007). */
class EditDoctorDuplicateLicenseTest extends AbstractAdminIntegrationTest {

    @Test
    void editingToAnotherProfilesLicenseNumberIsRejected() throws Exception {
        var profileA = saveDoctorProfile("doc.a@example.com", "LIC-A", "ENT", false);
        var profileB = saveDoctorProfile("doc.b@example.com", "LIC-B", "Radiology", false);

        mockMvc.perform(patch("/api/v1/admin/doctors/{id}", profileA.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "specialization": "ENT", "licenseNumber": "LIC-B", "experienceYears": 5, "visible": true }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_LICENSE_NUMBER"));

        assertThat(doctorProfileRepository.findById(profileA.getId()).orElseThrow().getLicenseNumber())
                .isEqualTo("LIC-A");
        assertThat(doctorProfileRepository.findById(profileB.getId()).orElseThrow().getLicenseNumber())
                .isEqualTo("LIC-B");
    }
}
