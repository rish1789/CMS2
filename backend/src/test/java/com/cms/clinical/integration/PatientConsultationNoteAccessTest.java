package com.cms.clinical.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.clinical.domain.ConsultationNote;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * 059-patient-clinical-record-access (quickstart.md Scenario 1/3): a patient reading their own
 * consultation note, and being refused another patient's. Adds patient-account-linked booking
 * fixtures locally (AbstractConsultationNoteIntegrationTest's own {@code bookSlot} creates a
 * walk-in Patient with no linked PatientAccount, which is correct for the staff-side tests it
 * already serves but not for this feature's own "self-service patient" scenario).
 */
class PatientConsultationNoteAccessTest extends AbstractConsultationNoteIntegrationTest {

    @Autowired
    private PatientAccountRepository patientAccountRepository;

    @Autowired
    private JwtService patientJwtService;

    @AfterEach
    void cleanPatientAccounts() {
        patientAccountRepository.deleteAll();
    }

    private PatientAccount savePatientAccount() {
        return patientAccountRepository.save(
                new PatientAccount("patient" + UUID.randomUUID() + "@example.com", "hash", "9" + System.nanoTime() % 1_000_000_000L));
    }

    private String patientToken(PatientAccount account) {
        return patientJwtService.issueToken(account.getId());
    }

    /** Books the given Slot for a self-service patient linked to the given PatientAccount, mirroring the base class's own bookSlot shape. */
    private Booking bookSlotForPatientAccount(Clinic clinic, DoctorProfile doctor, Slot slot, PatientAccount patientAccount) {
        AppointmentType appointmentType =
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Patient patient = patientRepository.save(new Patient(clinic, patientAccount, "Self-Service Patient", null));
        Booking booking = bookingRepository.saveAndFlush(
                new Booking(slot, patient, appointmentType, new BigDecimal("300.00"), doctor.getAccount().getId()));
        slot.setStatus(com.cms.scheduling.domain.SlotStatus.BOOKED);
        slotRepository.save(slot);
        return booking;
    }

    @Test
    void patientReadsTheirOwnConsultationNote() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount patientAccount = savePatientAccount();
        Booking booking = bookSlotForPatientAccount(clinic, doctor, slot, patientAccount);
        consultationNoteRepository.save(new ConsultationNote(booking, doctor, "Discussed symptoms, prescribed rest."));
        String token = patientToken(patientAccount);

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/consultation-note", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Discussed symptoms, prescribed rest."));
    }

    @Test
    void patientSeesNothingWhenNoNoteExists() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount patientAccount = savePatientAccount();
        Booking booking = bookSlotForPatientAccount(clinic, doctor, slot, patientAccount);
        String token = patientToken(patientAccount);

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/consultation-note", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        assertThat(consultationNoteRepository.findByBooking_Id(booking.getId())).isEmpty();
    }

    @Test
    void aPatientIsRefusedAnotherPatientsConsultationNote() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount owner = savePatientAccount();
        Booking booking = bookSlotForPatientAccount(clinic, doctor, slot, owner);
        consultationNoteRepository.save(new ConsultationNote(booking, doctor, "Confidential note."));

        PatientAccount someoneElse = savePatientAccount();
        String someoneElsesToken = patientToken(someoneElse);

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/consultation-note", booking.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + someoneElsesToken))
                .andExpect(status().isNotFound());
    }
}
