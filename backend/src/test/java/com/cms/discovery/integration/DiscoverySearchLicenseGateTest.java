package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** 035 FR-002, spec US1 AC2: an unverified doctor license excludes that doctor even at a verified clinic. */
class DiscoverySearchLicenseGateTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void unverifiedLicenseExcludesDoctorFromVerifiedClinic() throws Exception {
        var clinic = saveClinic("Verified Clinic", true);
        var profile = saveDoctorProfile("LIC-LICENSE-GATE", false, true);
        linkDoctorToClinic(profile, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
