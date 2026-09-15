package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** 035 FR-001, spec US1 AC7: no Authorization header of any kind is required or read. */
class DiscoverySearchNoAuthRequiredTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void requestWithNoAuthorizationHeaderSucceedsWithFullyGatedResults() throws Exception {
        var verifiedClinic = saveClinic("Verified Clinic", true);
        var unverifiedClinic = saveClinic("Unverified Clinic", false);
        var eligible = saveDoctorProfile("LIC-NO-AUTH-ELIGIBLE", true, true);
        var ineligible = saveDoctorProfile("LIC-NO-AUTH-INELIGIBLE", true, true);
        linkDoctorToClinic(eligible, verifiedClinic, true);
        linkDoctorToClinic(ineligible, unverifiedClinic, true);

        // No Authorization header is set at all on this request.
        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].doctorProfileId").value(eligible.getId().toString()));
    }
}
