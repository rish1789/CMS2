package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.ClinicAppointmentTypePrice;
import com.cms.booking.domain.ClinicDoctorFee;
import com.cms.booking.repository.ClinicAppointmentTypePriceRepository;
import com.cms.booking.repository.ClinicDoctorFeeRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.domain.Slot;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 068-per-clinic-fees US1 (FR-001..FR-004, SC-001): one doctor, two clinics, each with its own
 * prices - every booking locks its own clinic's price, a change at one clinic never touches the
 * other, and a clinic with no price blocks even when the other clinic has one.
 */
class PerClinicFeeBookingTest extends AbstractPatientBookingIntegrationTest {

    @Autowired
    private ClinicDoctorFeeRepository clinicDoctorFeeRepository;

    @Autowired
    private ClinicAppointmentTypePriceRepository clinicAppointmentTypePriceRepository;

    private Clinic clinicA;
    private Clinic clinicB;
    private DoctorProfile doctor;
    private AppointmentType consultation;
    private List<Slot> slotsAtA;
    private List<Slot> slotsAtB;

    @BeforeEach
    void doctorAtTwoClinics() {
        clinicA = saveClinic();
        clinicB = saveClinic();
        doctor = saveDoctorStaffedAt(clinicA);
        linkDoctorToClinic(doctor, clinicB, true);
        consultation = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", null));
        slotsAtA = slotRepository.findBySession_Id(saveFixedTimeSessionWithSlots(clinicA, doctor).getId());
        slotsAtB = slotRepository.findBySession_Id(saveFixedTimeSessionWithSlots(clinicB, doctor).getId());
    }

    /** Runs before the base cleanup: price rows reference clinic, doctor and appointment type. */
    @AfterEach
    void deletePrices() {
        clinicAppointmentTypePriceRepository.deleteAll();
        clinicDoctorFeeRepository.deleteAll();
    }

    @Test
    void eachBookingLocksItsOwnClinicsDefaultFee() throws Exception {
        setDefaultFee(clinicA, "300.00");
        setDefaultFee(clinicB, "500.00");

        book(clinicA, slotsAtA.get(0)).andExpect(status().isCreated()).andExpect(jsonPath("$.lockedFee").value(300.00));
        book(clinicB, slotsAtB.get(0)).andExpect(status().isCreated()).andExpect(jsonPath("$.lockedFee").value(500.00));
    }

    @Test
    void aPriceChangeAtOneClinicNeverChangesTheOther() throws Exception {
        ClinicDoctorFee feeAtA = setDefaultFee(clinicA, "300.00");
        setDefaultFee(clinicB, "500.00");

        feeAtA.update(new BigDecimal("350.00"), null);
        clinicDoctorFeeRepository.save(feeAtA);

        book(clinicA, slotsAtA.get(0)).andExpect(status().isCreated()).andExpect(jsonPath("$.lockedFee").value(350.00));
        book(clinicB, slotsAtB.get(0)).andExpect(status().isCreated()).andExpect(jsonPath("$.lockedFee").value(500.00));
    }

    @Test
    void aTypePriceAtOneClinicIsUsedOnlyThere() throws Exception {
        setDefaultFee(clinicA, "300.00");
        setDefaultFee(clinicB, "500.00");
        clinicAppointmentTypePriceRepository.save(
                new ClinicAppointmentTypePrice(clinicA, consultation, new BigDecimal("800.00"), null));

        book(clinicA, slotsAtA.get(0)).andExpect(status().isCreated()).andExpect(jsonPath("$.lockedFee").value(800.00));
        book(clinicB, slotsAtB.get(0)).andExpect(status().isCreated()).andExpect(jsonPath("$.lockedFee").value(500.00));
    }

    @Test
    void aClinicWithNoPriceBlocksEvenWhenAnotherClinicHasOne() throws Exception {
        setDefaultFee(clinicA, "300.00");

        book(clinicB, slotsAtB.get(0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NO_FEE_CONFIGURED"));
        book(clinicA, slotsAtA.get(0)).andExpect(status().isCreated()).andExpect(jsonPath("$.lockedFee").value(300.00));
    }

    private ClinicDoctorFee setDefaultFee(Clinic clinic, String amount) {
        return clinicDoctorFeeRepository.save(new ClinicDoctorFee(clinic, doctor, new BigDecimal(amount), null));
    }

    private ResultActions book(Clinic clinic, Slot slot) throws Exception {
        return mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), slot.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(savePatientAccount()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "patientName": "Per Clinic Patient", "appointmentTypeId": "%s" }
                        """.formatted(consultation.getId())));
    }
}
