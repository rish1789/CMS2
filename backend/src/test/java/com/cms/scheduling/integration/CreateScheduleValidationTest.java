package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 013 FR-005..FR-008, spec US1 AC3-AC5, Edge Cases: every field-shape validation rule. */
class CreateScheduleValidationTest extends AbstractScheduleIntegrationTest {

    private ResultActions submit(String clinicId, String doctorId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinicId, doctorId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void queueModeWithSlotIntervalIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        clinicAdminToken(clinic),
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "10:00", "mode": "QUEUE", "slotIntervalMinutes": 15 }
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SCHEDULE"));
    }

    @Test
    void fixedTimeModeWithNoSlotIntervalIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        clinicAdminToken(clinic),
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "10:00", "mode": "FIXED_TIME" }
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SCHEDULE"));
    }

    @Test
    void fixedTimeModeWithNonPositiveSlotIntervalIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        clinicAdminToken(clinic),
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "10:00", "mode": "FIXED_TIME", "slotIntervalMinutes": 0 }
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SCHEDULE"));
    }

    @Test
    void startTimeNotBeforeEndTimeIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        clinicAdminToken(clinic),
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "13:00", "endTime": "09:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SCHEDULE"));
    }

    @Test
    void emptyDaysOfWeekIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        clinicAdminToken(clinic),
                        """
                        { "daysOfWeek": [], "startTime": "09:00", "endTime": "10:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SCHEDULE"));
    }

    @Test
    void duplicateDayInSubmissionIsDeduplicatedNotRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        clinicAdminToken(clinic),
                        """
                        { "daysOfWeek": ["MONDAY", "MONDAY"], "startTime": "09:00", "endTime": "10:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.daysOfWeek", org.hamcrest.Matchers.hasSize(1)));
    }
}
