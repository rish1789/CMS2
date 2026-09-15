package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 013 FR-003, spec US1 AC7: an unrelated staff member is forbidden; no token is unauthorized. */
class CreateScheduleForbiddenTest extends AbstractScheduleIntegrationTest {

    private static final String VALID_QUEUE_BODY =
            """
            { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "10:00", "mode": "QUEUE" }
            """;

    @Test
    void unrelatedStaffMemberIsForbiddenOnCreate() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedStaffToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_QUEUE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void unrelatedStaffMemberIsForbiddenOnList() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedStaffToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_QUEUE_BODY))
                .andExpect(status().isUnauthorized());
    }
}
