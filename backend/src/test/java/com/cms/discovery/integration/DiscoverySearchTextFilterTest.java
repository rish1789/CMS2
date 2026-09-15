package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * 035 FR-005, spec US2 AC1-AC3: `q` matches (case-insensitive, substring) against
 * specialization, doctor name, clinic name, and clinic address independently, always
 * restricted to the already-eligible set.
 */
class DiscoverySearchTextFilterTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void matchesBySpecializationCaseInsensitiveSubstring() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var cardiologist = saveDoctorProfile("Dr. Asha Rao", "LIC-TXT-1", "Cardiology", true, true);
        var dermatologist = saveDoctorProfile("Dr. Bala Iyer", "LIC-TXT-2", "Dermatology", true, true);
        linkDoctorToClinic(cardiologist, clinic, true);
        linkDoctorToClinic(dermatologist, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("q", "cardio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].doctorProfileId").value(cardiologist.getId().toString()));
    }

    @Test
    void matchesByDoctorNameCaseInsensitiveSubstring() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);
        var asha = saveDoctorProfile("Dr. Asha Rao", "LIC-TXT-3", "Cardiology", true, true);
        var bala = saveDoctorProfile("Dr. Bala Iyer", "LIC-TXT-4", "Dermatology", true, true);
        linkDoctorToClinic(asha, clinic, true);
        linkDoctorToClinic(bala, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("q", "asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].doctorProfileId").value(asha.getId().toString()));
    }

    @Test
    void matchesByClinicNameCaseInsensitiveSubstring() throws Exception {
        var sunrise = saveClinic("Sunrise Clinic", "1 Health Ave", true);
        var moonlight = saveClinic("Moonlight Clinic", "2 Health Ave", true);
        var atSunrise = saveDoctorProfile("Dr. Test One", "LIC-TXT-5", "General Medicine", true, true);
        var atMoonlight = saveDoctorProfile("Dr. Test Two", "LIC-TXT-6", "General Medicine", true, true);
        linkDoctorToClinic(atSunrise, sunrise, true);
        linkDoctorToClinic(atMoonlight, moonlight, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("q", "sunrise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].clinicId").value(sunrise.getId().toString()));
    }

    @Test
    void matchesByClinicAddressCaseInsensitiveSubstring() throws Exception {
        var mgRoad = saveClinic("Clinic A", "10 MG Road", true);
        var parkStreet = saveClinic("Clinic B", "20 Park Street", true);
        var atMgRoad = saveDoctorProfile("Dr. Test Three", "LIC-TXT-7", "General Medicine", true, true);
        var atParkStreet = saveDoctorProfile("Dr. Test Four", "LIC-TXT-8", "General Medicine", true, true);
        linkDoctorToClinic(atMgRoad, mgRoad, true);
        linkDoctorToClinic(atParkStreet, parkStreet, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("q", "mg road"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].clinicId").value(mgRoad.getId().toString()));
    }

    @Test
    void textFilterNeverSurfacesAnIneligibleDoctorEvenOnAMatch() throws Exception {
        var unverifiedClinic = saveClinic("Sunrise Clinic", true);
        var ineligible = saveDoctorProfile("Dr. Asha Rao", "LIC-TXT-9", "Cardiology", false, true);
        linkDoctorToClinic(ineligible, unverifiedClinic, true);

        mockMvc.perform(get("/api/v1/discovery/search").param("q", "asha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
