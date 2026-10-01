package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.inbox.repository.InboxItemRepository;
import com.cms.patient.record.domain.Patient;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 074-duplicate-patient-phone (PB-001, PB-002): a new unlinked patient whose phone already belongs
 * to an unlinked patient at the same clinic is refused with one explicit conflict - naming the
 * existing record so staff can book them instead - on staff fixed-time booking, staff queue
 * booking and front-desk walk-in. Never a wrong "slot already booked" or a 500, never a merge, and
 * a refused request leaves nothing behind. Real Postgres; synthetic data only.
 */
class DuplicatePatientPhoneTest extends AbstractDeVerificationCascadeIntegrationTest {

    private static final String PHONE = "9876543210";

    @Autowired
    private InboxItemRepository inboxItemRepository;

    private Clinic clinic;
    private DoctorProfile doctor;
    private AppointmentType type;
    private String token;

    @BeforeEach
    void clinicWithADoctor() {
        clinic = saveClinic();
        doctor = saveDoctorStaffedAt(clinic);
        type = clinicPriceFixtures.priceAtStaffedClinics(
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", null)), new BigDecimal("300.00"));
        token = clinicAdminToken(clinic);
    }

    /** Runs before the superclass cleanup: inbox_item references clinic without a cascade. */
    @AfterEach
    void cleanInboxItems() {
        inboxItemRepository.deleteAll();
    }

    private Patient unlinkedPatientAt(Clinic at, String name) {
        return patientRepository.save(new Patient(at, null, name, PHONE));
    }

    // --- the three paths ---------------------------------------------------------------------

    private Session tomorrowsFixedSession() {
        return saveFixedTimeSessionWithSlotsOn(clinic, doctor, LocalDate.now().plusDays(1));
    }

    private ResultActions staffBook(Slot slot, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), slot.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientName\":\"%s\",\"patientPhone\":\"%s\",\"appointmentTypeId\":\"%s\"}"
                        .formatted(name, PHONE, type.getId())));
    }

    private ResultActions queueBook(Session session, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinic.getId(), session.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientName\":\"%s\",\"patientPhone\":\"%s\",\"appointmentTypeId\":\"%s\"}"
                        .formatted(name, PHONE, type.getId())));
    }

    private ResultActions walkIn(Session session, String name) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/walk-ins", clinic.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"sessionId\":\"%s\",\"patientName\":\"%s\",\"patientPhone\":\"%s\",\"patientEmail\":null,"
                                + "\"appointmentTypeId\":\"%s\",\"visitReason\":\"PAIN\",\"confirmDuplicate\":false}")
                        .formatted(session.getId(), name, PHONE, type.getId())));
    }

    private void expectConflictNaming(ResultActions result, Patient existing) throws Exception {
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PATIENT_PHONE_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.existingPatient.id").value(existing.getId().toString()))
                .andExpect(jsonPath("$.existingPatient.name").value(existing.getName()));
    }

    private void expectNothingWritten(long slotsBefore) {
        assertThat(bookingRepository.findAll()).isEmpty();
        assertThat(patientRepository.findAll()).hasSize(1);
        assertThat(slotRepository.count()).isEqualTo(slotsBefore);
        assertThat(inboxItemRepository.findAll()).isEmpty();
    }

    // --- US1: the explicit conflict ----------------------------------------------------------

    @Test
    void staffFixedTimeBookingReportsTheDuplicatePhoneNotSlotAlreadyBooked() throws Exception {
        Patient existing = unlinkedPatientAt(clinic, "Asha Rao");
        Slot slot = slotRepository.findBySession_Id(tomorrowsFixedSession().getId()).get(0);
        long slotsBefore = slotRepository.count();

        expectConflictNaming(staffBook(slot, "New Person"), existing);

        expectNothingWritten(slotsBefore);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
    }

    @Test
    void staffQueueBookingReportsTheDuplicatePhoneNotA500() throws Exception {
        Patient existing = unlinkedPatientAt(clinic, "Asha Rao");
        Session session = saveQueueSession(clinic, doctor);
        long slotsBefore = slotRepository.count();

        expectConflictNaming(queueBook(session, "New Person"), existing);

        // No token was issued for the refused request.
        expectNothingWritten(slotsBefore);
    }

    @Test
    void frontDeskWalkInReportsTheDuplicatePhoneNotA500() throws Exception {
        Patient existing = unlinkedPatientAt(clinic, "Asha Rao");
        Session session = saveQueueSession(clinic, doctor);
        long slotsBefore = slotRepository.count();

        expectConflictNaming(walkIn(session, "New Person"), existing);

        expectNothingWritten(slotsBefore);
    }

    @Test
    void theSamePhoneAtAnotherClinicIsIndependent() throws Exception {
        Clinic other = saveClinic();
        unlinkedPatientAt(other, "Someone Elsewhere");

        queueBook(saveQueueSession(clinic, doctor), "New Person").andExpect(status().isCreated());

        assertThat(patientRepository.findAll()).hasSize(2);
    }

    @Test
    void aLinkedPatientsPhoneDoesNotConflictAsTheIndexCoversOnlyUnlinkedRecords() throws Exception {
        patientRepository.save(new Patient(clinic, savePatientAccount(), "Linked Patient", PHONE));

        queueBook(saveQueueSession(clinic, doctor), "New Person").andExpect(status().isCreated());
    }

    // --- US2: concurrent registrations ---------------------------------------------------------

    private List<MvcResult> concurrently(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> call : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private void expectOneWinnerOneConflict(List<MvcResult> results) throws Exception {
        List<Integer> statuses = results.stream().map(r -> r.getResponse().getStatus()).sorted().toList();
        assertThat(statuses).containsExactly(201, 409);
        MvcResult loser = results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow();
        assertThat(loser.getResponse().getContentAsString()).contains("PATIENT_PHONE_ALREADY_REGISTERED");
        assertThat(patientRepository.findAll()).hasSize(1);
        assertThat(bookingRepository.findAll()).hasSize(1);
    }

    @Test
    void concurrentStaffFixedTimeBookingsOfTheSameNewPhoneCreateOnePatient() throws Exception {
        List<Slot> slots = slotRepository.findBySession_Id(tomorrowsFixedSession().getId());
        Slot a = slots.get(0);
        Slot b = slots.get(1);

        expectOneWinnerOneConflict(concurrently(
                () -> staffBook(a, "First Person").andReturn(), () -> staffBook(b, "Second Person").andReturn()));
        // The refused request's slot is still open.
        assertThat(slotRepository.findBySession_Id(a.getSession().getId()))
                .filteredOn(s -> s.getStatus() == SlotStatus.BOOKED)
                .hasSize(1);
    }

    @Test
    void concurrentQueueBookingsOfTheSameNewPhoneCreateOnePatientAndOneToken() throws Exception {
        Session session = saveQueueSession(clinic, doctor);

        expectOneWinnerOneConflict(concurrently(
                () -> queueBook(session, "First Person").andReturn(), () -> queueBook(session, "Second Person").andReturn()));
        assertThat(slotRepository.findBySession_Id(session.getId())).hasSize(1);
    }

    @Test
    void concurrentWalkInsOfTheSameNewPhoneCreateOnePatientAndOneToken() throws Exception {
        Session session = saveQueueSession(clinic, doctor);

        expectOneWinnerOneConflict(concurrently(
                () -> walkIn(session, "First Person").andReturn(), () -> walkIn(session, "Second Person").andReturn()));
        assertThat(slotRepository.findBySession_Id(session.getId())).hasSize(1);
        assertThat(inboxItemRepository.findAll()).hasSize(1);
    }
}
