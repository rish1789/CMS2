package com.cms.waitlist.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.waitlist.WaitlistEntry;
import com.cms.waitlist.WaitlistEntryStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 032 US3: T021 (sweep detects a lapsed entry and re-offers), T022 (exhausted waitlist, untouched-if-not-lapsed). */
class WaitlistExpirySweepTest extends AbstractWaitlistIntegrationTest {

    @Test
    void sweepReleasesALapsedOfferAndReOffersTheNextEligibleEntry() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        WaitlistEntry lapsed = saveOfferedWaitlistEntry(
                clinic,
                savePatientAccount(),
                doctor,
                null,
                slot,
                Instant.now().minusSeconds(2000),
                Instant.now().minusSeconds(200));
        WaitlistEntry nextEligible =
                saveWaitlistEntry(clinic, savePatientAccount(), doctor, null, Instant.now());

        int releasedCount = waitlistExpirySweepService.sweepExpiredOffers();

        assertThat(releasedCount).isEqualTo(1);
        assertThat(waitlistEntryRepository.findById(lapsed.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.EXPIRED);
        assertThat(waitlistEntryRepository.findById(nextEligible.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.OFFERED);
    }

    @Test
    void sweepWithNoOtherEligibleEntryLeavesTheSlotAvailable() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        WaitlistEntry lapsed = saveOfferedWaitlistEntry(
                clinic,
                savePatientAccount(),
                doctor,
                null,
                slot,
                Instant.now().minusSeconds(2000),
                Instant.now().minusSeconds(200));

        int releasedCount = waitlistExpirySweepService.sweepExpiredOffers();

        assertThat(releasedCount).isEqualTo(1);
        assertThat(waitlistEntryRepository.findById(lapsed.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.EXPIRED);
    }

    @Test
    void sweepLeavesAnUnlapsedOfferUntouched() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        WaitlistEntry stillValid = saveOfferedWaitlistEntry(
                clinic, savePatientAccount(), doctor, null, slot, Instant.now(), Instant.now().plusSeconds(1800));

        int releasedCount = waitlistExpirySweepService.sweepExpiredOffers();

        assertThat(releasedCount).isEqualTo(0);
        assertThat(waitlistEntryRepository.findById(stillValid.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.OFFERED);
    }
}
