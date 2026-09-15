package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * 035 FR-005, spec Edge Cases: an absent, empty, or whitespace-only `q` all behave
 * identically to no filter at all - the full eligible set is returned.
 */
class DiscoverySearchEmptyTermTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void absentEmptyAndWhitespaceOnlyTermAllReturnTheFullEligibleSet() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("LIC-EMPTY-TERM", true, true);
        linkDoctorToClinic(profile, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/v1/discovery/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/v1/discovery/search").param("q", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
