package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * 035 FR-004, spec US1 AC6: eligibility is evaluated live on every call, in both
 * directions - a de-verification removes a previously-eligible entry on the very next
 * call, and a subsequent re-verification makes it reappear on the call after that. No
 * caching, no separate re-listing step.
 */
class DiscoverySearchDeverificationLiveUpdateTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void clinicDeverificationThenReverificationIsReflectedOnTheVeryNextCallEachTime() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("LIC-DEVERIFY", true, true);
        linkDoctorToClinic(profile, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        clinic.setVerified(false);
        clinicRepository.save(clinic);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        clinic.setVerified(true);
        clinicRepository.save(clinic);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void doctorLicenseResetThenReverificationIsReflectedOnTheVeryNextCallEachTime() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("LIC-DEVERIFY-DOCTOR", true, true);
        linkDoctorToClinic(profile, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        profile.setLicenseVerified(false);
        doctorProfileRepository.save(profile);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        profile.setLicenseVerified(true);
        doctorProfileRepository.save(profile);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
