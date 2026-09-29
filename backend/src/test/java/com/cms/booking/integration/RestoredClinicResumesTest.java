package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.waitlist.domain.WaitlistEntry;
import com.cms.waitlist.domain.WaitlistEntryStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 062-rejected-clinic-gating (FR-005, User Story 3, SC-003, tasks.md T032): a reject -> restore
 * round trip leaves the clinic operating like any pending clinic - it takes a booking again with no
 * extra admin step, and the next generation run fills the dates skipped while it was rejected -
 * while what the rejection cancelled stays cancelled (the same "no restore" rule as 008).
 */
class RestoredClinicResumesTest extends AbstractDeVerificationCascadeIntegrationTest {

    @Autowired
    private JwtService patientJwtService;

    @Test
    void aRestoredClinicTakesBookingsAndGeneratesAgainButRejectionCancellationsStayCancelled() throws Exception {
        Clinic clinic = saveClinic();
        clinic.setVerified(false);
        clinic = clinicRepository.save(clinic);
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session any = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Session> sessionsBefore = sessionRepository.findBySchedule_Id(any.getSchedule().getId());
        Slot tomorrowFirst = firstSlotOn(sessionsBefore, LocalDate.now().plusDays(1));
        Booking cancelledByRejection = bookSlot(clinic, doctor, tomorrowFirst, savePatientAccount());
        WaitlistEntry expiredByRejection = saveWaitingEntry(clinic, doctor, savePatientAccount());

        clinicVerificationService.reject(clinic.getId(), "DUPLICATE_REGISTRATION", null, SUPER_ADMIN_USERNAME);
        // While rejected, a run for a later window generates nothing new for this clinic.
        LocalDate later = LocalDate.now().plusDays(20);
        sessionGenerationService.generate(later);
        assertThat(sessionRepository.findBySchedule_Id(any.getSchedule().getId())).hasSameSizeAs(sessionsBefore);

        clinicVerificationService.restore(clinic.getId());

        // FR-005: the next run fills the window that was skipped while rejected.
        sessionGenerationService.generate(later);
        assertThat(sessionRepository.findBySchedule_Id(any.getSchedule().getId()).size())
                .isGreaterThan(sessionsBefore.size());

        // FR-005/SC-003: a patient booking succeeds with no extra admin step.
        AppointmentType type = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        PatientAccount patient = savePatientAccount();
        Slot bookable = firstSlotOn(sessionRepository.findBySchedule_Id(any.getSchedule().getId()), LocalDate.now().plusDays(2));
        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), bookable.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientJwtService.issueToken(patient.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientName\":\"Asha\",\"appointmentTypeId\":\"" + type.getId() + "\"}"))
                .andExpect(status().isCreated());

        // Restore never reinstates what the rejection closed.
        assertThat(bookingRepository.findById(cancelledByRejection.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(waitlistEntryRepository.findById(expiredByRejection.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.EXPIRED);
    }

    private Slot firstSlotOn(List<Session> sessions, LocalDate date) {
        Session session = sessions.stream().filter(s -> s.getSessionDate().equals(date)).findFirst().orElseThrow();
        return slotRepository.findBySession_Id(session.getId()).stream()
                .min(Comparator.comparing(Slot::getStartTime))
                .orElseThrow();
    }
}
