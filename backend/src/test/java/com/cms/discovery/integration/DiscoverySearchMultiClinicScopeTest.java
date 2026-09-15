package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * 035 spec Edge Cases: a doctor holding active Role Assignments at two clinics, one
 * verified and one not, appears exactly once - scoped to the verified clinic only.
 */
class DiscoverySearchMultiClinicScopeTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void multiClinicDoctorAppearsOnlyForTheVerifiedClinic() throws Exception {
        var verifiedClinic = saveClinic("Verified Clinic", true);
        var unverifiedClinic = saveClinic("Unverified Clinic", false);
        var profile = saveDoctorProfile("LIC-MULTI-CLINIC", true, true);
        linkDoctorToClinic(profile, verifiedClinic, true);
        linkDoctorToClinic(profile, unverifiedClinic, true);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].clinicId").value(verifiedClinic.getId().toString()));
    }
}
