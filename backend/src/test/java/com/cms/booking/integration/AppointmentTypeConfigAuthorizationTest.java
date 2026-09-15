package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 017 FR-005/FR-006, spec US2 AC1-AC2, AC4: the Doctor and an authorized ClinicAdmin both succeed; an unrelated staff member is forbidden. */
class AppointmentTypeConfigAuthorizationTest extends AbstractBookingIntegrationTest {

    private static final String CREATE_BODY = """
            { "name": "Follow-up", "feeOverride": 300.00 }
            """;
    private static final String DEFAULT_FEE_BODY = """
            { "amount": 500.00 }
            """;

    @Test
    void doctorsOwnTokenSucceedsOnCreateListAndSetDefaultFee() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = doctorToken(doctor);

        mockMvc.perform(post("/api/v1/doctors/{id}/appointment-types", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/doctors/{id}/appointment-types", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(put("/api/v1/doctors/{id}/default-fee", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEFAULT_FEE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void clinicAdminAtDoctorsClinicSucceeds() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/doctors/{id}/appointment-types", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/doctors/{id}/default-fee", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEFAULT_FEE_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void unrelatedStaffMemberIsForbiddenOnAllThreeEndpoints() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = unrelatedStaffToken();

        mockMvc.perform(post("/api/v1/doctors/{id}/appointment-types", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/doctors/{id}/appointment-types", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/doctors/{id}/default-fee", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DEFAULT_FEE_BODY))
                .andExpect(status().isForbidden());
    }
}
