package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.ClinicBookingLimitOverride;
import com.cms.booking.domain.ClinicBookingLimitOverrideChangeLog;
import com.cms.booking.repository.ClinicBookingLimitOverrideChangeLogRepository;
import com.cms.booking.repository.ClinicBookingLimitOverrideRepository;
import com.cms.identity.clinic.Clinic;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * real-bug-fix 2026-09-24: bulk-deleting rejected clinics 500'd with a foreign-key violation
 * because deleteGuarded never cleared the per-clinic booking-limit override (060) - and since the
 * failure only surfaced at commit, it rolled back every other deletion in the same batch too.
 * Proves against a real Postgres that the override and its change log are cleared as clinic
 * configuration, and that the batch reports per-clinic results instead of failing outright.
 */
class RejectedClinicDeletionTest extends AbstractAdminIntegrationTest {

    @Autowired
    ClinicBookingLimitOverrideRepository overrideRepository;

    @Autowired
    ClinicBookingLimitOverrideChangeLogRepository overrideChangeLogRepository;

    private Clinic saveRejectedClinic(String name) {
        Clinic clinic = saveClinic(name, false);
        clinic.reject(Clinic.RejectionReason.DUPLICATE_REGISTRATION, null, SUPER_ADMIN_USERNAME);
        return clinicRepository.save(clinic);
    }

    @Test
    void bulkDeletingARejectedClinicWithABookingLimitOverrideSucceeds() throws Exception {
        Clinic clinic = saveRejectedClinic("Override Clinic");
        overrideRepository.save(new ClinicBookingLimitOverride(clinic, 5, Instant.now(), SUPER_ADMIN_USERNAME));
        overrideChangeLogRepository.save(
                new ClinicBookingLimitOverrideChangeLog(clinic, null, 5, Instant.now(), SUPER_ADMIN_USERNAME));

        mockMvc.perform(post("/api/v1/admin/clinics/delete-bulk")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + clinic.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded[0]").value(clinic.getId().toString()));

        assertFalse(clinicRepository.existsById(clinic.getId()));
        assertTrue(overrideRepository.findByClinic_Id(clinic.getId()).isEmpty());
        assertTrue(overrideChangeLogRepository
                .findByClinic_IdOrderByChangedAtDesc(clinic.getId())
                .isEmpty());
    }
}
