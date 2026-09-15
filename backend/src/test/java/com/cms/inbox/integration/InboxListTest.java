package com.cms.inbox.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.AppointmentType;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.Session;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/** 038 US1, T014-T015: FR-006/SC-003 clinic scoping, and the Operations-or-ClinicAdmin authorization gate. */
class InboxListTest extends AbstractInboxIntegrationTest {

    private ResultActions list(UUID clinicId, String token) throws Exception {
        return mockMvc.perform(
                get("/api/v1/clinics/{clinicId}/inbox", clinicId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void listReturnsOnlyItemsForTheRequestedClinic() throws Exception {
        Clinic clinicA = saveClinic();
        Clinic clinicB = saveClinic();
        DoctorProfile doctorA = saveDoctorStaffedAt(clinicA);
        DoctorProfile doctorB = saveDoctorStaffedAt(clinicB);
        Session sessionA = saveFixedTimeSessionWithSlots(clinicA, doctorA);
        Session sessionB = saveFixedTimeSessionWithSlots(clinicB, doctorB);
        AppointmentType typeA = saveAppointmentType(doctorA);
        AppointmentType typeB = saveAppointmentType(doctorB);
        insertWalkIn(operationsAccountId(clinicA), clinicA, sessionA, typeA);
        insertWalkIn(operationsAccountId(clinicB), clinicB, sessionB, typeB);

        list(clinicA.getId(), operationsToken(clinicA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].summary.bookingId").exists());
    }

    @Test
    void doctorCallerIsForbidden() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);

        list(clinic.getId(), doctorToken(doctor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        Clinic clinic = saveClinic();

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/inbox", clinic.getId()))
                .andExpect(status().isUnauthorized());
    }
}
