package com.cms.waitlist.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.booking.AppointmentType;
import com.cms.booking.Booking;
import com.cms.booking.SlotAlreadyBookedException;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.PatientAccount;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.waitlist.WaitlistEntry;
import com.cms.waitlist.WaitlistEntryStatus;
import com.cms.waitlist.WaitlistOfferNotClaimableException;
import com.cms.waitlist.dto.ClaimWaitlistRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 032 US1: T012 (successful claim), T013 (ownership/window rejections), T014 (lost race to an ordinary booking). */
class WaitlistClaimTest extends AbstractWaitlistIntegrationTest {

    @Test
    void claimingWithinTheWindowCreatesAConfirmedBooking() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount patientAccount = savePatientAccount();
        WaitlistEntry entry = saveOfferedWaitlistEntry(
                clinic, patientAccount, doctor, null, slot, Instant.now(), Instant.now().plusSeconds(1800));
        AppointmentType appointmentType =
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));

        Booking booking = waitlistClaimService.claim(
                entry.getId(), patientAccount.getId(), new ClaimWaitlistRequest(appointmentType.getId(), "Claimant"));

        assertThat(booking.getSlot().getId()).isEqualTo(slot.getId());
        assertThat(booking.getLockedFee()).isEqualByComparingTo("300.00");
        assertThat(waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.CLAIMED);
    }

    @Test
    void rejectsAClaimByANonOwningPatient() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        WaitlistEntry entry = saveOfferedWaitlistEntry(
                clinic, savePatientAccount(), doctor, null, slot, Instant.now(), Instant.now().plusSeconds(1800));
        PatientAccount someoneElse = savePatientAccount();
        AppointmentType appointmentType =
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));

        assertThatThrownBy(() -> waitlistClaimService.claim(
                        entry.getId(),
                        someoneElse.getId(),
                        new ClaimWaitlistRequest(appointmentType.getId(), "Someone Else")))
                .isInstanceOf(com.cms.waitlist.WaitlistEntryNotFoundException.class);
    }

    @Test
    void rejectsAClaimAgainstAWaitingEntry() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        PatientAccount patientAccount = savePatientAccount();
        WaitlistEntry entry = saveWaitlistEntry(clinic, patientAccount, doctor, null, Instant.now());
        AppointmentType appointmentType =
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));

        assertThatThrownBy(() -> waitlistClaimService.claim(
                        entry.getId(),
                        patientAccount.getId(),
                        new ClaimWaitlistRequest(appointmentType.getId(), "Claimant")))
                .isInstanceOf(WaitlistOfferNotClaimableException.class);
    }

    @Test
    void rejectsAClaimAgainstALapsedWindow() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount patientAccount = savePatientAccount();
        WaitlistEntry entry = saveOfferedWaitlistEntry(
                clinic,
                patientAccount,
                doctor,
                null,
                slot,
                Instant.now().minusSeconds(2000),
                Instant.now().minusSeconds(200));
        AppointmentType appointmentType =
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));

        assertThatThrownBy(() -> waitlistClaimService.claim(
                        entry.getId(),
                        patientAccount.getId(),
                        new ClaimWaitlistRequest(appointmentType.getId(), "Claimant")))
                .isInstanceOf(WaitlistOfferNotClaimableException.class);
    }

    @Test
    void claimLosesTheRaceToAnOrdinaryBooking() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Slot slot = slots.get(0);
        PatientAccount offeredPatient = savePatientAccount();
        WaitlistEntry entry = saveOfferedWaitlistEntry(
                clinic, offeredPatient, doctor, null, slot, Instant.now(), Instant.now().plusSeconds(1800));
        WaitlistEntry anotherEligible =
                saveWaitlistEntry(clinic, savePatientAccount(), doctor, null, Instant.now());
        AppointmentType appointmentType =
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));

        // Someone else books this exact Slot ordinarily before the claim happens.
        bookSlot(clinic, doctor, slot, savePatientAccount());

        assertThatThrownBy(() -> waitlistClaimService.claim(
                        entry.getId(),
                        offeredPatient.getId(),
                        new ClaimWaitlistRequest(appointmentType.getId(), "Claimant")))
                .isInstanceOf(SlotAlreadyBookedException.class);

        assertThat(waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.EXPIRED);
        // research.md R10: the Slot is no longer OPEN, so no other entry is offered it either.
        assertThat(waitlistEntryRepository.findById(anotherEligible.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.WAITING);
    }
}
