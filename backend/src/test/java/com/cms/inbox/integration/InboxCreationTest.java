package com.cms.inbox.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.AppointmentType;
import com.cms.booking.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.inbox.InboxItem;
import com.cms.inbox.InboxItemType;
import com.cms.patient.account.PatientAccount;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.waitlist.WaitlistEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 038 US1, T011-T013: a WALK_IN item is created when 020 inserts a walk-in; a WAITLIST_OFFER item
 * when 031 offers a Slot; a DEVERIFICATION_CASCADE item per affected clinic when 008 cascades.
 */
class InboxCreationTest extends AbstractInboxIntegrationTest {

    @Test
    void walkInInsertionCreatesInboxItemScopedToClinic() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        AppointmentType appointmentType = saveAppointmentType(doctor);
        UUID caller = operationsAccountId(clinic);

        Booking booking = insertWalkIn(caller, clinic, session, appointmentType);

        List<InboxItem> items = outstandingItemsOf(clinic);
        assertThat(items).hasSize(1);
        InboxItem item = items.get(0);
        assertThat(item.getItemType()).isEqualTo(InboxItemType.WALK_IN);
        assertThat(item.getBooking().getId()).isEqualTo(booking.getId());
        assertThat(item.getClinic().getId()).isEqualTo(clinic.getId());
    }

    @Test
    void waitlistOfferCreatesInboxItemScopedToClinic() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        Slot openSlot = aRegularOpenSlotOf(session);
        PatientAccount patientAccount = savePatientAccount();
        WaitlistEntry entry = saveWaitingEntry(clinic, patientAccount, doctor, Instant.now());

        triggerWaitlistOffer(session, openSlot);

        List<InboxItem> items = outstandingItemsOf(clinic);
        assertThat(items).hasSize(1);
        InboxItem item = items.get(0);
        assertThat(item.getItemType()).isEqualTo(InboxItemType.WAITLIST_OFFER);
        assertThat(item.getWaitlistEntry().getId()).isEqualTo(entry.getId());
    }

    @Test
    void deverificationCascadeCreatesOneInboxItemPerAffectedClinic() {
        Clinic clinicA = saveClinic();
        Clinic clinicB = saveClinic();
        DoctorProfile doctor = saveDoctorProfile();
        linkDoctorToClinic(doctor, clinicA, true);
        linkDoctorToClinic(doctor, clinicB, true);
        Session sessionA = saveFixedTimeSessionWithSlots(clinicA, doctor);
        Session sessionB = saveFixedTimeSessionWithSlots(clinicB, doctor);
        AppointmentType appointmentTypeA = saveAppointmentType(doctor);
        AppointmentType appointmentTypeB = saveAppointmentType(doctor);
        insertWalkIn(operationsAccountId(clinicA), clinicA, sessionA, appointmentTypeA);
        insertWalkIn(operationsAccountId(clinicB), clinicB, sessionB, appointmentTypeB);
        // Clear the two WALK_IN items this setup itself created, isolating this test to cascade notices only.
        inboxItemRepository.deleteAll();

        triggerDeverificationCascade(doctor.getId());

        List<InboxItem> itemsA = outstandingItemsOf(clinicA);
        List<InboxItem> itemsB = outstandingItemsOf(clinicB);
        assertThat(itemsA).hasSize(1);
        assertThat(itemsB).hasSize(1);
        assertThat(itemsA.get(0).getItemType()).isEqualTo(InboxItemType.DEVERIFICATION_CASCADE);
        assertThat(itemsA.get(0).getDoctorName()).isEqualTo(doctor.getAccount().getName());
        assertThat(itemsA.get(0).getCancelledBookingCount()).isEqualTo(1);
        assertThat(itemsB.get(0).getCancelledBookingCount()).isEqualTo(1);
    }
}
