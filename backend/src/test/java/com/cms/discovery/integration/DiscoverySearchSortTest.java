package com.cms.discovery.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * patient-search-advanced-filtering: sort spans the RoleAssignment/Account/Clinic/
 * DoctorProfile join this query is already built on - {@code doctorName}/{@code
 * clinicName} traverse the join (unlike the admin queues' deliberately root-only sort),
 * {@code experienceYears} stays on DoctorProfile. An unrecognized {@code sort} value
 * falls back to the default rather than 400ing (public endpoint).
 */
class DiscoverySearchSortTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void sortsByExperienceYearsDescending() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var junior = saveDoctorProfile("Dr. Junior", "LIC-SORT-1", "General Medicine", 2, true, true);
        var senior = saveDoctorProfile("Dr. Senior", "LIC-SORT-2", "General Medicine", 12, true, true);
        linkDoctorToClinic(junior, clinic, true);
        linkDoctorToClinic(senior, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search")
                        .param("sort", "experienceYears")
                        .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].doctorProfileId").value(senior.getId().toString()))
                .andExpect(jsonPath("$[1].doctorProfileId").value(junior.getId().toString()));
    }

    @Test
    void defaultSortIsDoctorNameAscending() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var bala = saveDoctorProfile("Dr. Bala Iyer", "LIC-SORT-3", "General Medicine", true, true);
        var asha = saveDoctorProfile("Dr. Asha Rao", "LIC-SORT-4", "General Medicine", true, true);
        linkDoctorToClinic(bala, clinic, true);
        linkDoctorToClinic(asha, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].doctorProfileId").value(asha.getId().toString()))
                .andExpect(jsonPath("$[1].doctorProfileId").value(bala.getId().toString()));
    }

    @Test
    void unrecognizedSortFieldFallsBackToTheDefaultInsteadOfErroring() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var profile = saveDoctorProfile("LIC-SORT-5", true, true);
        linkDoctorToClinic(profile, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("sort", "notARealField"))
                .andExpect(status().isOk());
    }
}
