package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.admin.ClinicDeVerifiedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * T016: un-verify sets false and publishes {@link ClinicDeVerifiedEvent} exactly once;
 * repeating the call on an already-unverified clinic is idempotent and does NOT
 * re-publish the event (FR-007, FR-008).
 */
@RecordApplicationEvents
class UnverifyClinicTest extends AbstractAdminIntegrationTest {

    @Autowired
    ApplicationEvents applicationEvents;

    @Test
    void unverifyingAVerifiedClinicSetsFalseAndPublishesEventOnce() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);

        mockMvc.perform(post("/api/v1/admin/clinics/{id}/unverify", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));

        assertFalse(clinicRepository.findById(clinic.getId()).orElseThrow().isVerified());
        assertEquals(
                1,
                applicationEvents
                        .stream(ClinicDeVerifiedEvent.class)
                        .filter(event -> event.clinicId().equals(clinic.getId()))
                        .count());
    }

    @Test
    void repeatingUnverifyOnAlreadyUnverifiedClinicDoesNotRePublishEvent() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);

        mockMvc.perform(post("/api/v1/admin/clinics/{id}/unverify", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk());

        // Second call - clinic is already unverified now.
        mockMvc.perform(post("/api/v1/admin/clinics/{id}/unverify", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false));

        assertEquals(
                1,
                applicationEvents
                        .stream(ClinicDeVerifiedEvent.class)
                        .filter(event -> event.clinicId().equals(clinic.getId()))
                        .count());
    }
}
