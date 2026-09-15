package com.cms.discovery.integration;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * 035 FR-002, spec US1 AC4: a doctor meeting all four conditions appears in results,
 * carrying their clinic's identity - and the response contains only listing-appropriate
 * fields, never a license number, verification flags, or credentials.
 *
 * <p>patient-search-advanced-filtering: 035's original FR-006 also excluded {@code
 * experienceYears} as a privacy choice - superseded here, since this feature explicitly
 * requires showing and filtering by it. licenseNumber/licenseVerified/visible remain hidden;
 * only the experience-years exclusion was ever about this specific field.
 */
class DiscoverySearchAllConditionsTest extends AbstractDiscoveryIntegrationTest {

    @Test
    void fullyEligibleDoctorAppearsWithClinicIdentityAndMinimalFields() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", "42 Health Ave", true);
        var profile = saveDoctorProfile("Dr. Asha Rao", "LIC-ALL-COND", "Cardiology", true, true);
        linkDoctorToClinic(profile, clinic, true);

        mockMvc.perform(get("/api/v1/discovery/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].doctorProfileId").value(profile.getId().toString()))
                .andExpect(jsonPath("$[0].doctorName").value("Dr. Asha Rao"))
                .andExpect(jsonPath("$[0].specialization").value("Cardiology"))
                .andExpect(jsonPath("$[0].clinicId").value(clinic.getId().toString()))
                .andExpect(jsonPath("$[0].clinicName").value("Sunrise Clinic"))
                .andExpect(jsonPath("$[0].clinicAddress").value("42 Health Ave"))
                .andExpect(jsonPath("$[0].experienceYears").value(5))
                .andExpect(jsonPath("$[0].licenseNumber").doesNotExist())
                .andExpect(jsonPath("$[0].licenseVerified").doesNotExist())
                .andExpect(jsonPath("$[0].visible").doesNotExist());
    }
}
