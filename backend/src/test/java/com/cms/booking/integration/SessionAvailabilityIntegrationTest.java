package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.inbox.repository.InboxItemRepository;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.protection.repository.SuspiciousActivityFlagRepository;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.SessionCancellation;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.waitlist.domain.WaitlistEntryStatus;
import java.math.BigDecimal;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 065-phase1-stabilization (tasks.md T025, v41-design-review.md §10 integration plan): the one
 * bookability rule, proven end to end against a real Postgres - listings, all five booking paths,
 * the waitlist offer, deletion protection and the V41 constraints.
 *
 * <p>Sessions are tomorrow's unless a case is specifically about "today", so no assertion depends
 * on the time of day the suite runs. The two today-based cases skip themselves near midnight.
 */
class SessionAvailabilityIntegrationTest extends AbstractDeVerificationCascadeIntegrationTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Autowired
    private JwtService patientJwtService;

    @Autowired
    private BookingAttemptLogRepository bookingAttemptLogRepository;

    @Autowired
    private SuspiciousActivityFlagRepository suspiciousActivityFlagRepository;

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Runs before the superclasses' clean-up: these rows reference clinic, patient_account and booking. */
    @AfterEach
    void cleanBookingSideRecords() {
        inboxItemRepository.deleteAll();
        suspiciousActivityFlagRepository.deleteAll();
        bookingAttemptLogRepository.deleteAll();
    }

    // ---- fixtures ----

    private Session tomorrowsSessionOf(Session anySessionOfSchedule) {
        return sessionOn(anySessionOfSchedule, TOMORROW);
    }

    private Session sessionOn(Session anySessionOfSchedule, LocalDate date) {
        return sessionRepository.findBySchedule_Id(anySessionOfSchedule.getSchedule().getId()).stream()
                .filter(s -> s.getSessionDate().equals(date))
                .findFirst()
                .orElseThrow();
    }

    private Slot slotAt(Session session, LocalTime start) {
        return slotRepository.findBySession_Id(session.getId()).stream()
                .filter(s -> start.equals(s.getStartTime()))
                .findFirst()
                .orElseThrow();
    }

    private AppointmentType appointmentTypeOf(DoctorProfile doctor) {
        return clinicPriceFixtures.priceAtStaffedClinics(
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", null)), new BigDecimal("300.00"));
    }

    /** A today-dated Fixed-Time session built by hand, with one slot already started and one still ahead. */
    private Session todaysSessionWithSlotsAt(Clinic clinic, DoctorProfile doctor, LocalTime... starts) {
        // Schedule-less (allowed since V34), so no generation run adds other sessions to the listing.
        Session session = sessionRepository.save(new Session(
                null, clinic, doctor, LocalDate.now(), ScheduleMode.FIXED_TIME, LocalTime.MIN, LocalTime.MAX, 15));
        for (LocalTime start : starts) {
            slotRepository.save(new Slot(session, start, start.plusMinutes(15)));
        }
        return session;
    }

    // ---- request helpers ----

    private String patientBearer(PatientAccount patient) {
        return "Bearer " + patientJwtService.issueToken(patient.getId());
    }

    private ResultActions cancelSession(Clinic clinic, Session session, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{c}/sessions/{s}/cancel", clinic.getId(), session.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions cancelRange(Clinic clinic, Session session, String token, LocalTime from, LocalTime to)
            throws Exception {
        String body = "{\"cutoffTime\":\"" + from + "\"" + (to == null ? "" : ",\"toTime\":\"" + to + "\"") + "}";
        return mockMvc.perform(post("/api/v1/clinics/{c}/sessions/{s}/cancel-from-cutoff", clinic.getId(), session.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions patientBook(Clinic clinic, Slot slot, PatientAccount patient, AppointmentType type)
            throws Exception {
        return mockMvc.perform(post("/api/v1/patients/clinics/{c}/slots/{s}/book", clinic.getId(), slot.getId())
                .header(HttpHeaders.AUTHORIZATION, patientBearer(patient))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientName\":\"Asha\",\"appointmentTypeId\":\"" + type.getId() + "\"}"));
    }

    private ResultActions staffBook(Clinic clinic, Slot slot, String token, AppointmentType type) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{c}/slots/{s}/book", clinic.getId(), slot.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientName\":\"Front Desk\",\"patientPhone\":\"9876543210\",\"appointmentTypeId\":\""
                        + type.getId() + "\"}"));
    }

    private ResultActions patientQueueBook(Clinic clinic, Session session, PatientAccount patient, AppointmentType type)
            throws Exception {
        return mockMvc.perform(post("/api/v1/patients/clinics/{c}/sessions/{s}/queue-bookings", clinic.getId(), session.getId())
                .header(HttpHeaders.AUTHORIZATION, patientBearer(patient))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientName\":\"Asha\",\"appointmentTypeId\":\"" + type.getId() + "\"}"));
    }

    private ResultActions staffQueueBook(Clinic clinic, Session session, String token, AppointmentType type)
            throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{c}/sessions/{s}/queue-bookings", clinic.getId(), session.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientName\":\"Front Desk\",\"patientPhone\":\"9876543210\",\"appointmentTypeId\":\""
                        + type.getId() + "\"}"));
    }

    private ResultActions walkIn(Clinic clinic, Session session, String token, AppointmentType type) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{c}/walk-ins", clinic.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"" + session.getId() + "\",\"patientName\":\"Walk In\",\"appointmentTypeId\":\""
                        + type.getId() + "\",\"visitReason\":\"PAIN\",\"confirmDuplicate\":false}"));
    }

    private ResultActions listSlotsOn(Clinic clinic, DoctorProfile doctor, LocalDate date, PatientAccount patient)
            throws Exception {
        return mockMvc.perform(get("/api/v1/patients/clinics/{c}/slots", clinic.getId())
                .param("doctorId", doctor.getId().toString())
                .param("date", date.toString())
                .param("size", "100")
                .header(HttpHeaders.AUTHORIZATION, patientBearer(patient)));
    }

    private static void refusedAsNotAccepting(ResultActions result) throws Exception {
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("SESSION_NOT_ACCEPTING_BOOKINGS"));
    }

    private static String slotJson(Slot slot) {
        return "$.slots[?(@.slotId=='" + slot.getId() + "')]";
    }

    // ---- whole-session cancellation (BUG-002) ----

    @Test
    void aWholeCancelledFixedTimeSessionIsUnlistedAndRefusedOnEveryPath() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = tomorrowsSessionOf(saveFixedTimeSessionWithSlots(clinic, doctor));
        AppointmentType type = appointmentTypeOf(doctor);
        Slot first = slotAt(session, LocalTime.of(9, 0));
        Booking booked = bookSlot(clinic, doctor, first);
        String admin = clinicAdminToken(clinic);
        PatientAccount patient = savePatientAccount();

        cancelSession(clinic, session, admin).andExpect(status().isOk()).andExpect(jsonPath("$.bookingsCancelled").value(1));
        assertThat(bookingRepository.findById(booked.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        // Slot semantics are unchanged: the cancelled booking's slot is OPEN again - but not bookable.
        assertThat(slotRepository.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
        long bookingsAfterCancel = bookingRepository.count();

        listSlotsOn(clinic, doctor, TOMORROW, patient).andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
        refusedAsNotAccepting(patientBook(clinic, first, patient, type));
        refusedAsNotAccepting(staffBook(clinic, slotAt(session, LocalTime.of(9, 15)), admin, type));
        refusedAsNotAccepting(walkIn(clinic, session, admin, type));
        assertThat(bookingRepository.count()).isEqualTo(bookingsAfterCancel);
    }

    @Test
    void aWholeCancelledQueueSessionIsUnlistedAndRefusesTokensAndWalkIns() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = tomorrowsSessionOf(saveQueueSession(clinic, doctor));
        AppointmentType type = appointmentTypeOf(doctor);
        String admin = clinicAdminToken(clinic);
        PatientAccount patient = savePatientAccount();

        cancelSession(clinic, session, admin).andExpect(status().isOk()).andExpect(jsonPath("$.bookingsCancelled").value(0));

        refusedAsNotAccepting(patientQueueBook(clinic, session, patient, type));
        refusedAsNotAccepting(staffQueueBook(clinic, session, admin, type));
        refusedAsNotAccepting(walkIn(clinic, session, admin, type));
        assertThat(slotRepository.findBySession_Id(session.getId())).isEmpty();
        mockMvc.perform(get("/api/v1/patients/clinics/{c}/queue-sessions", clinic.getId())
                        .param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, patientBearer(patient)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[?(@.sessionId=='" + session.getId() + "')]").doesNotExist());
    }

    // ---- empty session and repeat (BUG-004), deletion protection ----

    @Test
    void anEmptySessionCancelsOnceIsThenRefusedAndCannotBeDeleted() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = tomorrowsSessionOf(saveFixedTimeSessionWithSlots(clinic, doctor));
        String admin = clinicAdminToken(clinic);

        cancelSession(clinic, session, admin).andExpect(status().isOk()).andExpect(jsonPath("$.bookingsCancelled").value(0));
        cancelSession(clinic, session, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SESSION_ALREADY_CANCELLED"));
        mockMvc.perform(delete("/api/v1/clinics/{c}/sessions/{s}", clinic.getId(), session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SESSION_DELETION_BLOCKED"));

        List<SessionCancellation> records = sessionCancellationRepository.findBySession_Id(session.getId());
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getFromTime()).isNull();
        assertThat(records.get(0).getCancelledByAccountId()).isNotNull();
        assertThat(sessionRepository.findById(session.getId())).isPresent();
    }

    // ---- partial cancellation (BUG-003) ----

    @Test
    void aCancelledRangeIsUnlistedAndRefusedWhileTheRestOfTheSessionStaysBookable() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = tomorrowsSessionOf(saveFixedTimeSessionWithSlots(clinic, doctor));
        AppointmentType type = appointmentTypeOf(doctor);
        Booking beforeRange = bookSlot(clinic, doctor, slotAt(session, LocalTime.of(10, 0)));
        Booking insideRange = bookSlot(clinic, doctor, slotAt(session, LocalTime.of(11, 0)));
        String admin = clinicAdminToken(clinic);
        PatientAccount patient = savePatientAccount();

        cancelRange(clinic, session, admin, LocalTime.of(11, 0), LocalTime.of(12, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsCancelled").value(1));
        assertThat(bookingRepository.findById(beforeRange.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.ACTIVE);
        assertThat(bookingRepository.findById(insideRange.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);

        Slot justBefore = slotAt(session, LocalTime.of(10, 45));
        Slot rangeStart = slotAt(session, LocalTime.of(11, 0));
        Slot rangeEnd = slotAt(session, LocalTime.of(11, 45));
        Slot atToTime = slotAt(session, LocalTime.of(12, 0));
        listSlotsOn(clinic, doctor, TOMORROW, patient)
                .andExpect(status().isOk())
                .andExpect(jsonPath(slotJson(justBefore)).exists())
                .andExpect(jsonPath(slotJson(rangeStart)).doesNotExist())
                .andExpect(jsonPath(slotJson(rangeEnd)).doesNotExist())
                .andExpect(jsonPath(slotJson(atToTime)).exists());

        refusedAsNotAccepting(patientBook(clinic, rangeStart, patient, type));
        refusedAsNotAccepting(staffBook(clinic, rangeEnd, admin, type));
        patientBook(clinic, justBefore, patient, type).andExpect(status().is2xxSuccessful());
        staffBook(clinic, atToTime, admin, type).andExpect(status().is2xxSuccessful());

        // FR-011: ranges accumulate - a second, later range is recorded alongside the first.
        cancelRange(clinic, session, admin, LocalTime.of(12, 30), null).andExpect(status().isOk());
        assertThat(sessionCancellationRepository.findBySession_Id(session.getId()))
                .extracting(SessionCancellation::getFromTime, SessionCancellation::getToTime)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(11, 0), LocalTime.of(12, 0)),
                        org.assertj.core.groups.Tuple.tuple(LocalTime.of(12, 30), null));
    }

    @Test
    void aRangeCoveringNowRefusesTodaysQueueTokensAndWalkInsButALaterRangeDoesNot() throws Exception {
        LocalTime now = LocalTime.now();
        assumeTrue(now.isAfter(LocalTime.of(1, 0)) && now.isBefore(LocalTime.of(20, 0)), "needs room either side of now");
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session covered = sessionOn(saveQueueSession(clinic, doctor), LocalDate.now());
        Session laterOnly = sessionOn(saveQueueSession(clinic, doctor), LocalDate.now());
        AppointmentType type = appointmentTypeOf(doctor);
        String admin = clinicAdminToken(clinic);
        PatientAccount patient = savePatientAccount();

        cancelRange(clinic, covered, admin, now.minusHours(1), now.plusHours(1)).andExpect(status().isOk());
        cancelRange(clinic, laterOnly, admin, now.plusHours(2), null).andExpect(status().isOk());

        refusedAsNotAccepting(patientQueueBook(clinic, covered, patient, type));
        refusedAsNotAccepting(walkIn(clinic, covered, admin, type));
        patientQueueBook(clinic, laterOnly, patient, type).andExpect(status().is2xxSuccessful());
        walkIn(clinic, laterOnly, admin, type).andExpect(status().is2xxSuccessful());
    }

    // ---- elapsed and past slots (BUG-005) ----

    @Test
    void todaysElapsedSlotIsUnlistedAndRefusedWhileALaterSlotTodayIsBookable() throws Exception {
        LocalTime now = LocalTime.now();
        assumeTrue(now.isAfter(LocalTime.of(2, 30)) && now.isBefore(LocalTime.of(21, 0)), "needs room either side of now");
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        LocalTime elapsedStart = now.minusHours(2).withSecond(0).withNano(0);
        LocalTime laterStart = now.plusHours(2).withSecond(0).withNano(0);
        Session today = todaysSessionWithSlotsAt(clinic, doctor, elapsedStart, laterStart);
        Slot elapsed = slotAt(today, elapsedStart);
        Slot later = slotAt(today, laterStart);
        AppointmentType type = appointmentTypeOf(doctor);
        String admin = clinicAdminToken(clinic);
        PatientAccount patient = savePatientAccount();

        listSlotsOn(clinic, doctor, LocalDate.now(), patient)
                .andExpect(status().isOk())
                .andExpect(jsonPath(slotJson(elapsed)).doesNotExist())
                .andExpect(jsonPath(slotJson(later)).exists());

        patientBook(clinic, elapsed, patient, type)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_DATE_IN_THE_PAST"));
        staffBook(clinic, elapsed, admin, type)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_DATE_IN_THE_PAST"));
        patientBook(clinic, later, patient, type).andExpect(status().is2xxSuccessful());
    }

    @Test
    void aPastSessionRefusesQueueTokensAndWalkIns() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session queue = saveQueueSession(clinic, doctor);
        Session yesterday = sessionRepository.save(new Session(
                queue.getSchedule(), clinic, doctor, LocalDate.now().minusDays(1), ScheduleMode.QUEUE,
                LocalTime.of(9, 0), LocalTime.of(13, 0), null));
        AppointmentType type = appointmentTypeOf(doctor);
        String admin = clinicAdminToken(clinic);

        refusedAsNotAccepting(patientQueueBook(clinic, yesterday, savePatientAccount(), type));
        refusedAsNotAccepting(staffQueueBook(clinic, yesterday, admin, type));
        refusedAsNotAccepting(walkIn(clinic, yesterday, admin, type));
    }

    // ---- waitlist offer ----

    @Test
    void aSlotFreedInsideACancelledRangeIsNotOfferedToTheWaitlist() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = tomorrowsSessionOf(saveFixedTimeSessionWithSlots(clinic, doctor));
        Slot slot = slotAt(session, LocalTime.of(11, 0));
        Booking attended = bookSlot(clinic, doctor, slot);
        slot = slotRepository.findById(slot.getId()).orElseThrow();
        slot.setStatus(SlotStatus.APPEARED); // an attended visit survives the range cancellation
        slotRepository.save(slot);
        String admin = clinicAdminToken(clinic);
        cancelRange(clinic, session, admin, LocalTime.of(11, 0), null).andExpect(status().isOk());
        var entry = saveWaitingEntry(clinic, doctor, savePatientAccount());

        mockMvc.perform(post("/api/v1/clinics/{c}/bookings/{b}/cancel", clinic.getId(), attended.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk());

        assertThat(waitlistEntryRepository.findById(entry.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistEntryStatus.WAITING);
    }

    // ---- V41 constraints ----

    @Test
    void theDatabaseAllowsOneWholeCancellationPerSessionAndOnlyForwardRanges() {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = tomorrowsSessionOf(saveFixedTimeSessionWithSlots(clinic, doctor));
        UUID by = doctor.getAccount().getId();

        sessionCancellationRepository.saveAndFlush(SessionCancellation.whole(session, by, Instant.now()));
        assertThatThrownBy(() -> sessionCancellationRepository.saveAndFlush(SessionCancellation.whole(session, by, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO session_cancellation (session_id, from_time, to_time, cancelled_by_account_id)"
                                + " VALUES (?, ?, ?, ?)",
                        session.getId(), Time.valueOf(LocalTime.of(12, 0)), Time.valueOf(LocalTime.of(11, 0)), by))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(sessionCancellationRepository.findBySession_Id(session.getId())).hasSize(1);
    }
}
