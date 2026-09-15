package com.cms.discovery.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * patient-search-advanced-filtering: the two facet-listing endpoints that drive the City
 * and Specialization filter dropdowns - both scoped to the same eligibility gate as the
 * main search, so a picked value can never dead-end into zero results.
 */
class DiscoveryFacetsTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void citiesEndpointListsOnlyDistinctCitiesWithAnEligibleDoctor() throws Exception {
        var noida = saveClinic("Noida Clinic", "1 Sector Road", "Noida", true);
        var unverified = saveClinic("Unverified Clinic", "1 Test Road", "Ghost Town", false);
        var eligible = saveDoctorProfile("LIC-FACET-1", true, true);
        var ineligible = saveDoctorProfile("LIC-FACET-2", true, true);
        linkDoctorToClinic(eligible, noida, true);
        linkDoctorToClinic(ineligible, unverified, true);

        mockMvc.perform(get("/api/v1/discovery/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.contains("Noida")));
    }

    @Test
    void specializationsEndpointListsOnlyDistinctSpecializationsWithAnEligibleDoctor() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var cardiologist = saveDoctorProfile("Dr. Asha Rao", "LIC-FACET-3", "Cardiology", true, true);
        var invisible = saveDoctorProfile("Dr. Hidden", "LIC-FACET-4", "Dermatology", true, false);
        linkDoctorToClinic(cardiologist, clinic, true);
        linkDoctorToClinic(invisible, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/specializations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.contains("Cardiology")));
    }
}
