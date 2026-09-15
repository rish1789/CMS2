package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * patient-search-advanced-filtering: the hard "results are scoped to the selected city"
 * constraint - a city filter never returns a doctor from a different city, is
 * case-insensitive, and applies no scoping at all when omitted (so every 035 test that
 * never sends {@code city} keeps working unchanged).
 */
class DiscoverySearchCityFilterTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void cityFilterExcludesEligibleDoctorsInADifferentCity() throws Exception {
        var noida = saveClinic("Noida Clinic", "1 Sector Road", "Noida", true);
        var pune = saveClinic("Pune Clinic", "1 FC Road", "Pune", true);
        var inNoida = saveDoctorProfile("Dr. Noida", "LIC-CITY-1", "General Medicine", true, true);
        var inPune = saveDoctorProfile("Dr. Pune", "LIC-CITY-2", "General Medicine", true, true);
        linkDoctorToClinic(inNoida, noida, true);
        linkDoctorToClinic(inPune, pune, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("city", "Noida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].doctorProfileId").value(inNoida.getId().toString()));
    }

    @Test
    void cityFilterIsCaseInsensitive() throws Exception {
        var noida = saveClinic("Noida Clinic", "1 Sector Road", "Noida", true);
        var profile = saveDoctorProfile("LIC-CITY-3", true, true);
        linkDoctorToClinic(profile, noida, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("city", "noida"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void omittingCityAppliesNoScopingAtAll() throws Exception {
        var noida = saveClinic("Noida Clinic", "1 Sector Road", "Noida", true);
        var pune = saveClinic("Pune Clinic", "1 FC Road", "Pune", true);
        var inNoida = saveDoctorProfile("Dr. Noida", "LIC-CITY-4", "General Medicine", true, true);
        var inPune = saveDoctorProfile("Dr. Pune", "LIC-CITY-5", "General Medicine", true, true);
        linkDoctorToClinic(inNoida, noida, true);
        linkDoctorToClinic(inPune, pune, true);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
