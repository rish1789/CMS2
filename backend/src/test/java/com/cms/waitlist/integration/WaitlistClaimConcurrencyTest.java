package com.cms.waitlist.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.AppointmentType;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** 032 T022b (Analyze finding C1): exactly one of claim/decline/expiry ever succeeds against the same entry (FR-010/SC-004). */
class WaitlistClaimConcurrencyTest extends AbstractWaitlistIntegrationTest {

    @Test
    void claimAndDeclineRacingOnTheSameEntryOnlyOneWins() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount patientAccount = savePatientAccount();
        WaitlistEntry entry = saveOfferedWaitlistEntry(
                clinic, patientAccount, doctor, null, slot, Instant.now(), Instant.now().plusSeconds(1800));
        AppointmentType appointmentType =
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> claim = () -> {
                try {
                    waitlistClaimService.claim(
                            entry.getId(),
                            patientAccount.getId(),
                            new ClaimWaitlistRequest(appointmentType.getId(), "Claimant"));
                    return true;
                } catch (WaitlistOfferNotClaimableException e) {
                    return false;
                }
            };
            Callable<Boolean> decline = () -> {
                try {
                    waitlistClaimService.decline(entry.getId(), patientAccount.getId());
                    return true;
                } catch (WaitlistOfferNotClaimableException e) {
                    return false;
                }
            };

            List<Future<Boolean>> futures = executor.invokeAll(List.of(claim, decline));
            long winCount = futures.stream()
                    .filter(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .count();

            assertThat(winCount).isEqualTo(1);
            WaitlistEntryStatus finalStatus =
                    waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus();
            assertThat(finalStatus).isIn(WaitlistEntryStatus.CLAIMED, WaitlistEntryStatus.EXPIRED);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void claimAndExpirySweepRacingOnTheSameEntryOnlyOneWins() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).get(0);
        PatientAccount patientAccount = savePatientAccount();
        // Window already lapsed, so both the sweep and a (rejected-on-time) claim attempt race
        // the exact same expireIfOffered/claimIfOffered guard on this row.
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

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> claim = () -> {
                try {
                    waitlistClaimService.claim(
                            entry.getId(),
                            patientAccount.getId(),
                            new ClaimWaitlistRequest(appointmentType.getId(), "Claimant"));
                    return true;
                } catch (WaitlistOfferNotClaimableException e) {
                    return false;
                }
            };
            Callable<Boolean> sweep = () -> waitlistExpirySweepService.sweepExpiredOffers() > 0;

            executor.invokeAll(List.of(claim, sweep));

            // The window had already lapsed, so claimIfOffered's own time check means the claim
            // can never win here regardless of thread interleaving - the sweep always does.
            assertThat(waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus())
                    .isEqualTo(WaitlistEntryStatus.EXPIRED);
        } finally {
            executor.shutdown();
        }
    }
}
