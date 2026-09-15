package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 014 FR-001..FR-006, spec US1 AC1-AC6: the cross-clinic/same-clinic overlap block. */
class CreateScheduleOverlapTest extends AbstractScheduleIntegrationTest {

    private ResultActions submit(String clinicId, String doctorId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/doctors/{doctorId}/schedules", clinicId, doctorId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void crossClinicOverlapIsRejected() throws Exception {
        var clinicA = saveClinic();
        var clinicB = saveClinic();
        var doctor = saveDoctorProfile();
        linkDoctorToClinic(doctor, clinicA, true);
        linkDoctorToClinic(doctor, clinicB, true);

        submit(
                        clinicA.getId().toString(),
                        doctor.getId().toString(),
                        clinicAdminToken(clinicA),
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "11:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isCreated());

        submit(
                        clinicB.getId().toString(),
                        doctor.getId().toString(),
                        clinicAdminToken(clinicB),
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "10:00", "endTime": "12:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SCHEDULE_OVERLAP"));
    }

    @Test
    void sameClinicOverlapIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        token,
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "11:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isCreated());

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        token,
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "10:00", "endTime": "11:30", "mode": "QUEUE" }
                        """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SCHEDULE_OVERLAP"));
    }

    @Test
    void touchingRangesAreAllowed() throws Exception {
        var clinicA = saveClinic();
        var clinicB = saveClinic();
        var doctor = saveDoctorProfile();
        linkDoctorToClinic(doctor, clinicA, true);
        linkDoctorToClinic(doctor, clinicB, true);

        submit(
                        clinicA.getId().toString(),
                        doctor.getId().toString(),
                        clinicAdminToken(clinicA),
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "11:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isCreated());

        submit(
                        clinicB.getId().toString(),
                        doctor.getId().toString(),
                        clinicAdminToken(clinicB),
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "11:00", "endTime": "13:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isCreated());
    }

    @Test
    void disjointDaysAreAllowedRegardlessOfTime() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        token,
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "11:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isCreated());

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        token,
                        """
                        { "daysOfWeek": ["TUESDAY"], "startTime": "09:00", "endTime": "11:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isCreated());
    }

    @Test
    void fullyContainedRangeIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        token,
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "17:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isCreated());

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        token,
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "10:00", "endTime": "11:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SCHEDULE_OVERLAP"));
    }

    @Test
    void firstScheduleForADoctorIsNeverRejectedForOverlap() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        clinicAdminToken(clinic),
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "11:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isCreated());
    }

    @Test
    void structurallyInvalidAndOverlappingRequestReturnsInvalidScheduleNotOverlap() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);

        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        token,
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "09:00", "endTime": "11:00", "mode": "QUEUE" }
                        """)
                .andExpect(status().isCreated());

        // Overlaps the schedule above AND is structurally invalid (QUEUE with a slot interval) -
        // the pre-existing 013 validation error must win, per research.md's precedence decision.
        submit(
                        clinic.getId().toString(),
                        doctor.getId().toString(),
                        token,
                        """
                        { "daysOfWeek": ["MONDAY"], "startTime": "10:00", "endTime": "11:00", "mode": "QUEUE", "slotIntervalMinutes": 15 }
                        """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SCHEDULE"));
    }
}
