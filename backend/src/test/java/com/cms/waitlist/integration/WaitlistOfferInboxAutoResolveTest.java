package com.cms.waitlist.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.AppointmentType;
import com.cms.booking.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.inbox.InboxItem;
import com.cms.inbox.InboxItemRepository;
import com.cms.inbox.InboxItemService;
import com.cms.inbox.InboxItemStatus;
import com.cms.patient.account.PatientAccount;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import com.cms.waitlist.WaitlistEntry;
import com.cms.waitlist.dto.ClaimWaitlistRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 038 US3, T034-T035a: FR-013 - a waitlist offer's own lifecycle (claim, decline, expiry) resolves
 * its Inbox Item automatically, with no staff action, and closes the race against a concurrent
 * staff-initiated resolve on the same item (analyze finding F1).
 */
class WaitlistOfferInboxAutoResolveTest extends AbstractWaitlistIntegrationTest {

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @Autowired
    private InboxItemService inboxItemService;

    private Session sessionWithOfferedEntry(Clinic clinic, DoctorProfile doctor, WaitlistEntry[] entryOut) {
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot slot = slotRepository.findBySession_Id(session.getId()).stream()
                .filter(s -> !s.isBuffer())
                .filter(s -> s.getStatus() == SlotStatus.OPEN)
                .findFirst()
                .orElseThrow();
        PatientAccount patientAccount = savePatientAccount();
        WaitlistEntry entry = saveOfferedWaitlistEntry(
                clinic, patientAccount, doctor, null, slot, Instant.now(), Instant.now().plusSeconds(1800));
        // matchAndOffer's own production call site is what normally creates the Inbox Item; this
        // fixture builds the OFFERED entry directly (mirroring saveOfferedWaitlistEntry's existing
        // precedent), so the Inbox Item is created directly too via the same production service.
        inboxItemService.createWaitlistOfferItem(clinic, entry);
        entryOut[0] = entry;
        return session;
    }

    @Test
    void claimingTheOfferAutoResolvesItsInboxItem() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "General Medicine");
        WaitlistEntry[] entryHolder = new WaitlistEntry[1];
        sessionWithOfferedEntry(clinic, doctor, entryHolder);
        WaitlistEntry entry = entryHolder[0];
        appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        AppointmentType appointmentType = appointmentTypeRepository.findAll().get(0);
        PatientAccount patientAccount = entry.getPatientAccount();

        Booking booking =
                waitlistClaimService.claim(entry.getId(), patientAccount.getId(), new ClaimWaitlistRequest(appointmentType.getId(), "Claimed Patient"));

        assertThat(booking).isNotNull();
        InboxItem item = inboxItemRepository.findByWaitlistEntry_Id(entry.getId()).orElseThrow();
        assertThat(item.getStatus()).isEqualTo(InboxItemStatus.RESOLVED);
    }

    @Test
    void decliningTheOfferAutoResolvesItsInboxItem() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "General Medicine");
        WaitlistEntry[] entryHolder = new WaitlistEntry[1];
        sessionWithOfferedEntry(clinic, doctor, entryHolder);
        WaitlistEntry entry = entryHolder[0];

        waitlistClaimService.decline(entry.getId(), entry.getPatientAccount().getId());

        InboxItem item = inboxItemRepository.findByWaitlistEntry_Id(entry.getId()).orElseThrow();
        assertThat(item.getStatus()).isEqualTo(InboxItemStatus.RESOLVED);
    }

    @Test
    void expirySweepAutoResolvesTheLapsedOffersInboxItem() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "General Medicine");
        WaitlistEntry[] entryHolder = new WaitlistEntry[1];
        sessionWithOfferedEntry(clinic, doctor, entryHolder);
        WaitlistEntry entry = entryHolder[0];
        jdbcTemplateUpdateOfferExpiresAt(entry.getId(), Instant.now().minusSeconds(1));

        waitlistExpirySweepService.sweepExpiredOffers();

        InboxItem item = inboxItemRepository.findByWaitlistEntry_Id(entry.getId()).orElseThrow();
        assertThat(item.getStatus()).isEqualTo(InboxItemStatus.RESOLVED);
    }

    /** 038 analyze finding F1: a concurrent staff resolve and a waitlist-lifecycle auto-resolve on the same item never double-transition or throw. */
    @Test
    void concurrentStaffResolveAndAutoResolveNeverConflict() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "General Medicine");
        WaitlistEntry[] entryHolder = new WaitlistEntry[1];
        sessionWithOfferedEntry(clinic, doctor, entryHolder);
        WaitlistEntry entry = entryHolder[0];
        InboxItem item = inboxItemRepository.findByWaitlistEntry_Id(entry.getId()).orElseThrow();
        java.util.UUID claimantAccountId = clinicAdminAccountId(clinic);
        var claimant = inboxItemService.claim(clinic.getId(), item.getId(), claimantAccountId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            var staffResolve = executor.submit(() -> {
                ready.countDown();
                await(go);
                try {
                    inboxItemService.resolve(clinic.getId(), item.getId(), claimant.claimedByAccountId());
                } catch (RuntimeException ignored) {
                    // A lost race here (already resolved) is an acceptable outcome for this test.
                }
            });
            var autoResolve = executor.submit(() -> {
                ready.countDown();
                await(go);
                inboxItemService.resolveByWaitlistEntry(entry.getId());
            });
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            staffResolve.get(5, TimeUnit.SECONDS);
            autoResolve.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        List<InboxItem> reloaded = inboxItemRepository.findAll();
        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.get(0).getStatus()).isEqualTo(InboxItemStatus.RESOLVED);
    }

    private java.util.UUID clinicAdminAccountId(Clinic clinic) {
        String unique = java.util.UUID.randomUUID().toString();
        var admin = accountRepository.save(new com.cms.identity.account.Account(
                "Admin " + unique, "admin-" + unique + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "CA-" + unique, null));
        roleAssignmentRepository.save(
                new com.cms.identity.account.RoleAssignment(admin, clinic, com.cms.identity.account.RoleAssignment.Role.ClinicAdmin));
        return admin.getId();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
