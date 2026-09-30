package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.BookingSource;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.inbox.repository.InboxItemRepository;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 067 US2 (FR-003, FR-005, SC-003): walk-in registrations for one session, submitted at the same
 * moment through the real endpoint, all succeed with distinct, sequential tokens - for a Queue
 * session and for a Fixed-Time session's walk-in line. Before 067, issuance joined the registration
 * transaction, so the first token collision aborted the whole registration.
 */
class WalkInConcurrentRegistrationTest extends AbstractDeVerificationCascadeIntegrationTest {

    private static final int WALK_INS = 10;

    @Autowired
    private InboxItemRepository inboxItemRepository;

    /** Runs before the superclass cleanup: inbox_item references clinic without a cascade. */
    @AfterEach
    void cleanInboxItems() {
        inboxItemRepository.deleteAll();
    }

    @Test
    void simultaneousQueueWalkInsAllGetDistinctSequentialTokens() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = saveType(doctor);
        Session session = tomorrow(saveQueueSession(clinic, doctor));

        List<MockHttpServletResponse> responses = registerConcurrently(clinic, clinicAdminToken(clinic),
                IntStream.range(0, WALK_INS).mapToObj(i -> newPatient(session.getId(), type, "Queue Walkin " + i)).toList());

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201));
        assertThat(walkInTokens(session)).containsExactlyInAnyOrderElementsOf(oneTo(WALK_INS));
    }

    @Test
    void simultaneousFixedTimeWalkInsAllGetDistinctSequentialLinePlaces() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = saveType(doctor);
        Session session = saveFixedTimeSessionWithSlotsOn(clinic, doctor, LocalDate.now().plusDays(1));

        List<MockHttpServletResponse> responses = registerConcurrently(clinic, clinicAdminToken(clinic),
                IntStream.range(0, WALK_INS).mapToObj(i -> newPatient(session.getId(), type, "Line Walkin " + i)).toList());

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201));
        assertThat(walkInTokens(session)).containsExactlyInAnyOrderElementsOf(oneTo(WALK_INS));
    }

    /** US2 scenario 2 (FR-004, FR-005): one duplicate walk-in in the burst is refused alone, leaving nothing behind. */
    @Test
    void aDuplicateWalkInInTheBurstIsRefusedAloneAndLeavesNoToken() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        AppointmentType type = saveType(doctor);
        Session session = tomorrow(saveQueueSession(clinic, doctor));
        String token = clinicAdminToken(clinic);

        // An existing walk-in (token 1); registering that same patient again unconfirmed is the duplicate.
        assertThat(registerConcurrently(clinic, token, List.of(newPatient(session.getId(), type, "First Walkin"))).get(0).getStatus())
                .isEqualTo(201);
        UUID existingPatientId = bookingRepository.findAll().get(0).getPatient().getId();
        String duplicate = "{\"sessionId\":\"" + session.getId() + "\",\"patientId\":\"" + existingPatientId
                + "\",\"appointmentTypeId\":\"" + type.getId() + "\",\"visitReason\":\"FOLLOW_UP\",\"confirmDuplicate\":false}";

        List<String> bodies = new ArrayList<>(IntStream.range(0, 5).mapToObj(i -> newPatient(session.getId(), type, "Burst Walkin " + i)).toList());
        bodies.add(duplicate);
        List<MockHttpServletResponse> responses = registerConcurrently(clinic, token, bodies);

        assertThat(responses.subList(0, 5)).allSatisfy(r -> assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201));
        assertThat(responses.get(5).getStatus()).isEqualTo(409);
        assertThat(walkInTokens(session)).containsExactlyInAnyOrderElementsOf(oneTo(6));
    }

    private AppointmentType saveType(DoctorProfile doctor) {
        return appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
    }

    private Session tomorrow(Session anySessionOfSchedule) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        return sessionRepository.findBySchedule_Id(anySessionOfSchedule.getSchedule().getId()).stream()
                .filter(s -> s.getSessionDate().equals(tomorrow))
                .findFirst()
                .orElseThrow();
    }

    private static String newPatient(UUID sessionId, AppointmentType type, String name) {
        return "{\"sessionId\":\"" + sessionId + "\",\"patientName\":\"" + name + "\",\"patientEmail\":null"
                + ",\"appointmentTypeId\":\"" + type.getId() + "\",\"visitReason\":\"PAIN\",\"confirmDuplicate\":false}";
    }

    private List<MockHttpServletResponse> registerConcurrently(Clinic clinic, String token, List<String> bodies) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(bodies.size());
        try {
            List<Callable<MockHttpServletResponse>> calls = bodies.stream()
                    .<Callable<MockHttpServletResponse>>map(body -> () -> mockMvc.perform(post("/api/v1/clinics/{clinicId}/walk-ins", clinic.getId())
                                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                            .andReturn()
                            .getResponse())
                    .toList();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> f : executor.invokeAll(calls)) {
                responses.add(f.get());
            }
            return responses;
        } finally {
            executor.shutdown();
        }
    }

    /** Token numbers of every walk-in booking in the session; a token without a booking would not appear. */
    private List<Integer> walkInTokens(Session session) {
        List<Integer> bookedTokens = bookingRepository.findAll().stream()
                .filter(b -> b.getSource() == BookingSource.WALK_IN && b.getSlot().getSession().getId().equals(session.getId()))
                .map(b -> b.getSlot().getTokenNumber())
                .toList();
        List<Integer> allTokens = slotRepository.findBySession_Id(session.getId()).stream()
                .map(Slot::getTokenNumber)
                .filter(t -> t != null)
                .toList();
        // FR-005: no token exists without its walk-in booking.
        assertThat(allTokens).containsExactlyInAnyOrderElementsOf(bookedTokens);
        return bookedTokens;
    }

    private static List<Integer> oneTo(int n) {
        return IntStream.rangeClosed(1, n).boxed().toList();
    }
}
