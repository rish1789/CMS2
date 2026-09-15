package com.cms.waitlist.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.AppointmentType;
import com.cms.booking.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.PatientAccount;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.waitlist.WaitlistEntry;
import com.cms.waitlist.WaitlistEntryStatus;
import com.cms.waitlist.dto.ClaimWaitlistRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 032 T022a (Analyze finding C2): FR-008's "repeats until claimed or exhausted" chained across a decline, then an expiry, then a successful claim. */
class WaitlistMultiHopCascadeTest extends AbstractWaitlistIntegrationTest {

    @Test
    void declineThenExpiryThenClaimCascadesThroughThreeEntries() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);

        PatientAccount firstPatient = savePatientAccount();
        PatientAccount secondPatient = savePatientAccount();
        PatientAccount thirdPatient = savePatientAccount();

        // Longest-waiting first, so matching offers them in this exact order.
        WaitlistEntry first = saveOfferedWaitlistEntry(
                clinic, firstPatient, doctor, null, slot, Instant.now(), Instant.now().plusSeconds(1800));
        WaitlistEntry second = saveWaitlistEntry(clinic, secondPatient, doctor, null, Instant.now().minusSeconds(120));
        WaitlistEntry third = saveWaitlistEntry(clinic, thirdPatient, doctor, null, Instant.now().minusSeconds(60));

        // Hop 1: first declines.
        waitlistClaimService.decline(first.getId(), firstPatient.getId());
        assertThat(waitlistEntryRepository.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.EXPIRED);
        WaitlistEntry refreshedSecond = waitlistEntryRepository.findById(second.getId()).orElseThrow();
        assertThat(refreshedSecond.getStatus()).isEqualTo(WaitlistEntryStatus.OFFERED);

        // Hop 2: second's window lapses; backdate it and run the sweep.
        jdbcTemplateUpdateOfferExpiresAt(second.getId(), Instant.now().minusSeconds(1));
        int releasedCount = waitlistExpirySweepService.sweepExpiredOffers();
        assertThat(releasedCount).isEqualTo(1);
        assertThat(waitlistEntryRepository.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.EXPIRED);
        WaitlistEntry refreshedThird = waitlistEntryRepository.findById(third.getId()).orElseThrow();
        assertThat(refreshedThird.getStatus()).isEqualTo(WaitlistEntryStatus.OFFERED);

        // Hop 3: third successfully claims.
        AppointmentType appointmentType =
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Booking booking = waitlistClaimService.claim(
                third.getId(), thirdPatient.getId(), new ClaimWaitlistRequest(appointmentType.getId(), "Claimant"));

        assertThat(booking.getSlot().getId()).isEqualTo(slot.getId());
        assertThat(waitlistEntryRepository.findById(third.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.CLAIMED);
    }
}
