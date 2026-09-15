package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** 035 FR-002, spec US1 AC5: an inactive Role Assignment excludes an otherwise-eligible doctor. */
class DiscoverySearchActiveRoleAssignmentGateTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void inactiveRoleAssignmentExcludesOtherwiseEligibleDoctor() throws Exception {
        var clinic = saveClinic("Verified Clinic", true);
        var profile = saveDoctorProfile("LIC-ROLE-GATE", true, true);
        linkDoctorToClinic(profile, clinic, false);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
