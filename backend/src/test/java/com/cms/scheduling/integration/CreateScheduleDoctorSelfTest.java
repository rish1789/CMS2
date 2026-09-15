package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 013 FR-002/FR-010, spec US2: a Doctor manages their own schedule; may never name another doctor. */
class CreateScheduleDoctorSelfTest extends AbstractScheduleIntegrationTest {

    private static final String VALID_QUEUE_BODY =
            """
            { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "10:00", "mode": "QUEUE" }
            """;

    @Test
    void doctorCreatesAndListsTheirOwnScheduleWithTheirOwnToken() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = doctorToken(doctor);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_QUEUE_BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void doctorCreatingAScheduleForADifferentDoctorIsForbidden() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var otherDoctor = saveDoctorStaffedAt(clinic);
        String otherDoctorToken = doctorToken(otherDoctor);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherDoctorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_QUEUE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
