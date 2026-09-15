package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * patient-search-advanced-filtering: the {@code specialization} filter is an exact,
 * case-insensitive match - independent of (and ANDable with) the free-text {@code q}
 * filter, which already does substring specialization matching.
 */
class DiscoverySearchSpecializationFilterTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void specializationFilterExcludesEligibleDoctorsWithADifferentSpecialization() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var cardiologist = saveDoctorProfile("Dr. Asha Rao", "LIC-SPEC-1", "Cardiology", true, true);
        var dermatologist = saveDoctorProfile("Dr. Bala Iyer", "LIC-SPEC-2", "Dermatology", true, true);
        linkDoctorToClinic(cardiologist, clinic, true);
        linkDoctorToClinic(dermatologist, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("specialization", "Cardiology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].doctorProfileId").value(cardiologist.getId().toString()));
    }

    @Test
    void specializationFilterIsExactMatchNotSubstring() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("Dr. Test", "LIC-SPEC-3", "Cardiology", true, true);
        linkDoctorToClinic(profile, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("specialization", "Cardio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
