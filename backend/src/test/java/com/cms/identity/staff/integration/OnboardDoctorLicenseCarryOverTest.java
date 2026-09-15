package com.cms.identity.staff.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T021: reusing an existing Doctor Profile for a second clinic never touches
 * licenseVerified - it carries over unchanged whether true or false beforehand
 * (FR-002c, SC-008).
 */
class OnboardDoctorLicenseCarryOverTest extends AbstractStaffIntegrationTest {

    @Test
    void reuseLeavesAnAlreadyVerifiedProfileVerified() throws Exception {
        Clinic clinicA = saveClinic("Sunrise Clinic");
        Clinic clinicB = saveClinic("Riverside Clinic");
        String tokenA = clinicAdminToken(clinicA);
        String tokenB = clinicAdminToken(clinicB);
        String licenseNumber = "LIC-CARRYOVER-VERIFIED";

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinicA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorRequestJson("dr.verified@sunrise-clinic.example", "ENT", licenseNumber)))
                .andExpect(status().isCreated());
        DoctorProfile profile = doctorProfileRepository.findByLicenseNumber(licenseNumber).orElseThrow();
        profile.setLicenseVerified(true);
        doctorProfileRepository.save(profile);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinicB.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorRequestJson("dr.verified2@riverside-clinic.example", "ENT", licenseNumber)))
                .andExpect(status().isCreated());

        assertThat(doctorProfileRepository.findByLicenseNumber(licenseNumber).orElseThrow().isLicenseVerified())
                .isTrue();
    }

    @Test
    void reuseLeavesAnUnverifiedProfileUnverified() throws Exception {
        Clinic clinicA = saveClinic("Sunrise Clinic");
        Clinic clinicB = saveClinic("Riverside Clinic");
        String tokenA = clinicAdminToken(clinicA);
        String tokenB = clinicAdminToken(clinicB);
        String licenseNumber = "LIC-CARRYOVER-UNVERIFIED";

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinicA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorRequestJson("dr.unverified@sunrise-clinic.example", "ENT", licenseNumber)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinicB.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorRequestJson("dr.unverified2@riverside-clinic.example", "ENT", licenseNumber)))
                .andExpect(status().isCreated());

        assertThat(doctorProfileRepository.findByLicenseNumber(licenseNumber).orElseThrow().isLicenseVerified())
                .isFalse();
    }
}
