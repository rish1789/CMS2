package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.ClinicDoctorFee;
import com.cms.booking.repository.ClinicDoctorFeeRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import java.math.BigDecimal;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 068-per-clinic-fees: a clinic's prices are its configuration - permanently deleting a rejected
 * clinic clears them instead of failing on a foreign key. A clinic's default fee for a rejected
 * doctor is real setup, so it blocks that doctor's delete like the old doctor-wide fee did.
 */
class ClinicPriceDeletionGuardTest extends AbstractAdminIntegrationTest {

    @Autowired
    private ClinicDoctorFeeRepository clinicDoctorFeeRepository;

    /** Runs before the base cleanup: price rows reference clinic and doctor. */
    @AfterEach
    void deletePrices() {
        clinicDoctorFeeRepository.deleteAll();
    }

    @Test
    void deletingARejectedClinicClearsItsPrices() throws Exception {
        Clinic clinic = saveClinic("Priced Clinic", false);
        DoctorProfile doctor = saveDoctorProfile("priced-doctor@example.com", "LIC-PRICE-1", "General", true);
        clinicDoctorFeeRepository.save(new ClinicDoctorFee(clinic, doctor, new BigDecimal("400.00"), null));
        clinic.reject(Clinic.RejectionReason.DUPLICATE_REGISTRATION, null, SUPER_ADMIN_USERNAME);
        clinicRepository.save(clinic);

        mockMvc.perform(post("/api/v1/admin/clinics/delete-bulk")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + clinic.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded[0]").value(clinic.getId().toString()));

        assertFalse(clinicRepository.existsById(clinic.getId()));
        assertTrue(clinicDoctorFeeRepository.findAll().isEmpty());
    }

    @Test
    void aClinicDefaultFeeBlocksDeletingARejectedDoctor() throws Exception {
        Clinic clinic = saveClinic("Fee Clinic", true);
        DoctorProfile doctor = saveDoctorProfile("rejected-doctor@example.com", "LIC-PRICE-2", "General", false);
        clinicDoctorFeeRepository.save(new ClinicDoctorFee(clinic, doctor, new BigDecimal("400.00"), null));
        doctor.reject(DoctorProfile.RejectionReason.DUPLICATE_REGISTRATION, null, SUPER_ADMIN_USERNAME);
        doctorProfileRepository.save(doctor);

        mockMvc.perform(post("/api/v1/admin/doctors/delete-bulk")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + doctor.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").isEmpty())
                .andExpect(jsonPath("$.failed['" + doctor.getId() + "']").value(Matchers.containsString("a default fee")));

        assertTrue(doctorProfileRepository.existsById(doctor.getId()));
    }
}
