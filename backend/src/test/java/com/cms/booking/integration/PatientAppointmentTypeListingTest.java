package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.doctor.DoctorProfile;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Patient-facing appointment-types listing for a doctor, backing {@code ClaimOfferCard}'s
 * picker. Unlike {@code BookingController.list} (staff-only, "this doctor or a ClinicAdmin
 * staffed where they work"), this endpoint has no ownership check at all - a patient is neither,
 * and the same fee/service-menu data is already exposed to any authenticated patient through
 * the open-slots/queue-sessions listings.
 */
class PatientAppointmentTypeListingTest extends AbstractPatientBookingIntegrationTest {

    @Test
    void listsADoctorsAppointmentTypesForAnyAuthenticatedPatient() throws Exception {
        DoctorProfile doctor = saveDoctorProfile();
        saveAppointmentTypeWithOverride(doctor, new BigDecimal("500.00"));
        saveAppointmentTypeWithNoOverride(doctor);
        var patientAccount = savePatientAccount();

        mockMvc.perform(get("/api/v1/patients/doctors/{doctorProfileId}/appointment-types", doctor.getId())
                        .header("Authorization", "Bearer " + patientToken(patientAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void returnsAnEmptyListForADoctorWithNoAppointmentTypesConfigured() throws Exception {
        DoctorProfile doctor = saveDoctorProfile();
        var patientAccount = savePatientAccount();

        mockMvc.perform(get("/api/v1/patients/doctors/{doctorProfileId}/appointment-types", doctor.getId())
                        .header("Authorization", "Bearer " + patientToken(patientAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void noTokenIsRejected() throws Exception {
        DoctorProfile doctor = saveDoctorProfile();

        mockMvc.perform(get("/api/v1/patients/doctors/{doctorProfileId}/appointment-types", doctor.getId()))
                .andExpect(status().isUnauthorized());
    }
}
