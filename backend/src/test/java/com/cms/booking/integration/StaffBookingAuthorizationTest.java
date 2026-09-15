package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 020 FR-002, spec US1 AC4: Operations and ClinicAdmin succeed; the Doctor/an unrelated staff member is forbidden; no token is unauthorized. */
class StaffBookingAuthorizationTest extends AbstractStaffBookingIntegrationTest {

    private ResultActions book(String clinicId, String slotId, String token, String body) throws Exception {
        var request = post("/api/v1/clinics/{clinicId}/slots/{slotId}/book", clinicId, slotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    @Test
    void operationsAndClinicAdminBothSucceedDoctorAndUnrelatedStaffAreForbidden() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));

        var session1 = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot1 = anOpenSlotOf(session1);
        var patient1 = saveExistingPatient(clinic);
        book(clinic.getId().toString(), slot1.getId().toString(), operationsToken(clinic),
                        """
                        { "patientId": "%s", "appointmentTypeId": "%s" }
                        """.formatted(patient1.getId(), appointmentType.getId()))
                .andExpect(status().isCreated());

        var slots = slotRepository.findBySession_Id(session1.getId());
        var slot2 = slots.stream().filter(s -> !s.getId().equals(slot1.getId())).findFirst().orElseThrow();
        var patient2 = saveExistingPatient(clinic);
        book(clinic.getId().toString(), slot2.getId().toString(), clinicAdminToken(clinic),
                        """
                        { "patientId": "%s", "appointmentTypeId": "%s" }
                        """.formatted(patient2.getId(), appointmentType.getId()))
                .andExpect(status().isCreated());

        var slot3 = slots.stream()
                .filter(s -> !s.getId().equals(slot1.getId()) && !s.getId().equals(slot2.getId()))
                .findFirst()
                .orElseThrow();
        var patient3 = saveExistingPatient(clinic);
        book(clinic.getId().toString(), slot3.getId().toString(), doctorToken(doctor),
                        """
                        { "patientId": "%s", "appointmentTypeId": "%s" }
                        """.formatted(patient3.getId(), appointmentType.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        book(clinic.getId().toString(), slot3.getId().toString(), unrelatedStaffToken(),
                        """
                        { "patientId": "%s", "appointmentTypeId": "%s" }
                        """.formatted(patient3.getId(), appointmentType.getId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void noTokenIsUnauthorized() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);

        book(clinic.getId().toString(), slot.getId().toString(), null,
                        """
                        { "patientName": "X", "appointmentTypeId": "%s" }
                        """.formatted(java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
