package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** 035 FR-005, spec US2 AC4: a `q` matching nothing returns 200 with an empty array, not an error. */
class DiscoverySearchNoMatchTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void nonMatchingSearchTermReturnsEmptyArrayNotAnError() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("Dr. Asha Rao", "LIC-NO-MATCH", "Cardiology", true, true);
        linkDoctorToClinic(profile, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("q", "doesnotmatchanything"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
