package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** 035 FR-002, spec US1 AC1: an unverified clinic excludes the clinic and all its doctors. */
class DiscoverySearchClinicGateTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void unverifiedClinicExcludesClinicAndAllItsDoctors() throws Exception {
        var clinic = saveClinic("Unverified Clinic", false);
        var profile = saveDoctorProfile("LIC-CLINIC-GATE", true, true);
        linkDoctorToClinic(profile, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
