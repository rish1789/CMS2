package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.ClinicDoctorFee;
import com.cms.booking.repository.ClinicDoctorFeeRepository;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * 068-per-clinic-fees US4 (FR-010/FR-011): staff readiness and every patient-facing price are
 * the clinic's own. A doctor priced at A but not at B is bookable at A and "setup incomplete" at
 * B, and patients see A's price only at A.
 */
class PerClinicFeeVisibilityTest extends AbstractPatientBookingIntegrationTest {

    @Autowired
    private ClinicDoctorFeeRepository clinicDoctorFeeRepository;

    @Autowired
    private StaffJwtService staffJwtService;

    private Clinic clinicA;
    private Clinic clinicB;
    private DoctorProfile doctor;
    private AppointmentType consultation;

    @BeforeEach
    void doctorPricedOnlyAtA() {
        clinicA = saveClinic();
        clinicB = saveClinic();
        doctor = saveDoctorStaffedAt(clinicA);
        linkDoctorToClinic(doctor, clinicB, true);
        consultation = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", null));
        clinicDoctorFeeRepository.save(new ClinicDoctorFee(clinicA, doctor, new BigDecimal("450.00"), null));
    }

    @Test
    void readinessIsPerClinic() throws Exception {
        String token = "Bearer " + staffJwtService.issueToken(doctor.getAccount().getId());

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors/booking-readiness", clinicA.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasDefaultFee").value(true))
                .andExpect(jsonPath("$[0].bookingReady").value(true));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/doctors/booking-readiness", clinicB.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].hasDefaultFee").value(false))
                .andExpect(jsonPath("$[0].hasAppointmentTypeMissingFeeOverride").value(true))
                .andExpect(jsonPath("$[0].bookingReady").value(false));
    }

    @Test
    void theClinicScopedPatientListingCarriesThatClinicsFee() throws Exception {
        String token = "Bearer " + patientToken(savePatientAccount());

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/doctors/{doctorProfileId}/appointment-types",
                                clinicA.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(consultation.getId().toString()))
                .andExpect(jsonPath("$[0].fee").value(450.00));

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/doctors/{doctorProfileId}/appointment-types",
                                clinicB.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fee").isEmpty());
    }

    /** No clinic context, so no price can be stated - the old path keeps the names only. */
    @Test
    void theDoctorOnlyPatientListingStatesNoFee() throws Exception {
        mockMvc.perform(get("/api/v1/patients/doctors/{doctorProfileId}/appointment-types", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(savePatientAccount())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Consultation"))
                .andExpect(jsonPath("$[0].fee").isEmpty());
    }

    @Test
    void openSlotAndQueueSessionListingsEmbedTheClinicsFee() throws Exception {
        saveFixedTimeSessionWithSlots(clinicA, doctor);
        saveFixedTimeSessionWithSlots(clinicB, doctor);
        saveQueueSessionWithOneOpenSlot(clinicA, doctor);
        String token = "Bearer " + patientToken(savePatientAccount());

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinicA.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].appointmentTypes[0].fee").value(450.00));
        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinicB.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slots[0].appointmentTypes[0].fee").isEmpty());
        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/queue-sessions", clinicA.getId())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].appointmentTypes[0].fee").value(450.00));
    }
}
