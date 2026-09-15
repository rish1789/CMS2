package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 013 FR-009, spec US1 AC6, Edge Cases: doctor not (actively) staffed at the clinic, and unknown clinic/doctor ids. */
class CreateScheduleStaffingGateTest extends AbstractScheduleIntegrationTest {

    private static final String VALID_QUEUE_BODY =
            """
            { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "10:00", "mode": "QUEUE" }
            """;

    @Test
    void doctorWithNoRoleAssignmentAtClinicIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorProfile(); // never linked to this clinic

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_QUEUE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DOCTOR_NOT_STAFFED_AT_CLINIC"));
    }

    @Test
    void doctorWithInactiveRoleAssignmentAtClinicIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorProfile();
        linkDoctorToClinic(doctor, clinic, false);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_QUEUE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DOCTOR_NOT_STAFFED_AT_CLINIC"));
    }

    @Test
    void unknownClinicIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", UUID.randomUUID(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_QUEUE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CLINIC_NOT_FOUND"));
    }

    @Test
    void unknownDoctorIsRejected() throws Exception {
        var clinic = saveClinic();

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinic.getId(), UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_QUEUE_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("DOCTOR_PROFILE_NOT_FOUND"));
    }
}
