package com.cms.clinical.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.clinical.domain.Prescription;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.account.repository.PatientAccountRepository;
import com.cms.patient.record.domain.Patient;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * 059-patient-clinical-record-access (quickstart.md Scenario 1/3): a patient reading their own
 * prescriptions, and being refused another patient's. See PatientConsultationNoteAccessTest for
 * why the patient-account fixture is local to this file rather than the shared abstract base
 * (this codebase's own established fixture-duplication pattern).
 */
class PatientPrescriptionAccessTest extends AbstractPrescriptionIntegrationTest {

    @Autowired
    private PatientAccountRepository patientAccountRepository;

    @Autowired
    private JwtService patientJwtService;

    private PatientAccount savePatientAccount() {
        return patientAccountRepository.save(
                new PatientAccount("patient" + UUID.randomUUID() + "@example.com", "hash", "9" + System.nanoTime() % 1_000_000_000L));
    }

    private String patientToken(PatientAccount account) {
        return patientJwtService.issueToken(account.getId());
    }

    private Booking bookSlotForPatientAccount(Clinic clinic, DoctorProfile doctor, Slot slot, PatientAccount patientAccount) {
        AppointmentType appointmentType =
                clinicPriceFixtures.priceAtStaffedClinics(
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", null)), new BigDecimal("300.00"));
        Patient patient = patientRepository.save(new Patient(clinic, patientAccount, "Self-Service Patient", null));
        Booking booking = bookingRepository.saveAndFlush(
                new Booking(slot, patient, appointmentType, new BigDecimal("300.00"), doctor.getAccount().getId()));
        slot.setStatus(com.cms.scheduling.domain.SlotStatus.BOOKED);
        slotRepository.save(slot);
        return booking;
    }

    @Test
    void patientReadsTheirOwnPrescriptions() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount patientAccount = savePatientAccount();
        Booking booking = bookSlotForPatientAccount(clinic, doctor, slot, patientAccount);
        Prescription prescription = new Prescription(booking, doctor);
        prescription.addItem("Paracetamol", "500mg", "Twice daily", "5 days", "After food");
        prescriptionRepository.save(prescription);
        String token = patientToken(patientAccount);

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/prescriptions", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items[0].medicationName").value("Paracetamol"));
    }

    @Test
    void patientSeesAnEmptyListWhenNoPrescriptionExists() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount patientAccount = savePatientAccount();
        Booking booking = bookSlotForPatientAccount(clinic, doctor, slot, patientAccount);
        String token = patientToken(patientAccount);

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/prescriptions", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void aPatientIsRefusedAnotherPatientsPrescriptions() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount owner = savePatientAccount();
        Booking booking = bookSlotForPatientAccount(clinic, doctor, slot, owner);
        Prescription prescription = new Prescription(booking, doctor);
        prescription.addItem("Paracetamol", "500mg", "Twice daily", "5 days", "After food");
        prescriptionRepository.save(prescription);

        PatientAccount someoneElse = savePatientAccount();
        String someoneElsesToken = patientToken(someoneElse);

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/prescriptions", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + someoneElsesToken))
                .andExpect(status().isNotFound());
    }
}
