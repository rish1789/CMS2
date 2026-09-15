package com.cms.waitlist.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.PatientAccount;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.waitlist.WaitlistEntry;
import com.cms.waitlist.WaitlistEntryNotFoundException;
import com.cms.waitlist.WaitlistEntryStatus;
import com.cms.waitlist.WaitlistOfferNotClaimableException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 032 US2: T017 (decline re-offers the next eligible entry), T018 (exhausted waitlist, ownership, non-OFFERED rejections). */
class WaitlistDeclineTest extends AbstractWaitlistIntegrationTest {

    @Test
    void decliningReOffersTheNextEligibleEntry() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount offeredPatient = savePatientAccount();
        WaitlistEntry offered = saveOfferedWaitlistEntry(
                clinic, offeredPatient, doctor, null, slot, Instant.now(), Instant.now().plusSeconds(1800));
        WaitlistEntry nextEligible =
                saveWaitlistEntry(clinic, savePatientAccount(), doctor, null, Instant.now());

        WaitlistEntry declined = waitlistClaimService.decline(offered.getId(), offeredPatient.getId());

        assertThat(declined.getStatus()).isEqualTo(WaitlistEntryStatus.EXPIRED);
        WaitlistEntry refreshedNext = waitlistEntryRepository.findById(nextEligible.getId()).orElseThrow();
        assertThat(refreshedNext.getStatus()).isEqualTo(WaitlistEntryStatus.OFFERED);
        assertThat(refreshedNext.getOfferedSlot().getId()).isEqualTo(slot.getId());
    }

    @Test
    void decliningWithNoOtherEligibleEntryLeavesTheSlotAvailable() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount offeredPatient = savePatientAccount();
        WaitlistEntry offered = saveOfferedWaitlistEntry(
                clinic, offeredPatient, doctor, null, slot, Instant.now(), Instant.now().plusSeconds(1800));

        WaitlistEntry declined = waitlistClaimService.decline(offered.getId(), offeredPatient.getId());

        assertThat(declined.getStatus()).isEqualTo(WaitlistEntryStatus.EXPIRED);
        assertThat(waitlistEntryRepository.findAll()).hasSize(1);
    }

    @Test
    void rejectsADeclineByANonOwningPatient() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        WaitlistEntry offered = saveOfferedWaitlistEntry(
                clinic, savePatientAccount(), doctor, null, slot, Instant.now(), Instant.now().plusSeconds(1800));
        PatientAccount someoneElse = savePatientAccount();

        assertThatThrownBy(() -> waitlistClaimService.decline(offered.getId(), someoneElse.getId()))
                .isInstanceOf(WaitlistEntryNotFoundException.class);
    }

    @Test
    void rejectsADeclineAgainstANonOfferedEntry() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        PatientAccount patientAccount = savePatientAccount();
        WaitlistEntry waiting = saveWaitlistEntry(clinic, patientAccount, doctor, null, Instant.now());

        assertThatThrownBy(() -> waitlistClaimService.decline(waiting.getId(), patientAccount.getId()))
                .isInstanceOf(WaitlistOfferNotClaimableException.class);
    }
}
