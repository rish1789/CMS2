package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** patient-search-advanced-filtering: {@code minExperienceYears} is a >= threshold, not an exact match. */
class DiscoverySearchExperienceFilterTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void minExperienceYearsExcludesDoctorsBelowTheThreshold() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var junior = saveDoctorProfile("Dr. Junior", "LIC-EXP-1", "General Medicine", 2, true, true);
        var senior = saveDoctorProfile("Dr. Senior", "LIC-EXP-2", "General Medicine", 12, true, true);
        linkDoctorToClinic(junior, clinic, true);
        linkDoctorToClinic(senior, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("minExperienceYears", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].doctorProfileId").value(senior.getId().toString()));
    }

    @Test
    void thresholdIsInclusiveOfAnExactMatch() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("Dr. Exact", "LIC-EXP-3", "General Medicine", 10, true, true);
        linkDoctorToClinic(profile, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("minExperienceYears", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
