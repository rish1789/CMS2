package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;

/** 013 FR-001/FR-004, spec US1 AC1-AC2: a ClinicAdmin creates Fixed-Time and Queue/Token schedules. */
class CreateScheduleClinicAdminTest extends AbstractScheduleIntegrationTest {

    @Test
    void clinicAdminCreatesFixedTimeSchedulePersistedAndListable() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "daysOfWeek": ["MONDAY", "WEDNESDAY", "FRIDAY"],
                                  "startTime": "09:00",
                                  "endTime": "13:00",
                                  "mode": "FIXED_TIME",
                                  "slotIntervalMinutes": 15
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("FIXED_TIME"))
                .andExpect(jsonPath("$.slotIntervalMinutes").value(15))
                .andExpect(jsonPath("$.daysOfWeek", org.hamcrest.Matchers.containsInAnyOrder("MONDAY", "WEDNESDAY", "FRIDAY")));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    void clinicAdminCreatesQueueScheduleWithNoSlotInterval() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "daysOfWeek": ["TUESDAY", "THURSDAY"],
                                  "startTime": "14:00",
                                  "endTime": "17:00",
                                  "mode": "QUEUE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("QUEUE"))
                .andExpect(jsonPath("$.slotIntervalMinutes").value(org.hamcrest.Matchers.nullValue()));
    }
}
