package com.cms.inbox.integration;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.domain.Booking;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.DeVerificationCascadeService;
import com.cms.booking.service.FrontDeskWalkInService;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.repository.AccountRepository;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.inbox.domain.InboxItem;
import com.cms.inbox.repository.InboxItemRepository;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.account.repository.PatientAccountRepository;
import com.cms.patient.record.repository.PatientRepository;
import com.cms.scheduling.domain.Schedule;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.repository.ScheduleRepository;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.service.SessionGenerationService;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.waitlist.domain.WaitlistEntry;
import com.cms.waitlist.repository.WaitlistEntryRepository;
import com.cms.waitlist.service.WaitlistMatchingService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 038: combines 025's walk-in fixture shape with 031's waitlist fixture shape, plus direct
 * triggers for all three Inbox item sources (020/029/008) so tests exercise the real production
 * call sites rather than seeding InboxItem rows directly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractInboxIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("cms_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ClinicRepository clinicRepository;

    @Autowired
    protected AccountRepository accountRepository;

    @Autowired
    protected RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    protected DoctorProfileRepository doctorProfileRepository;

    @Autowired
    protected PatientAccountRepository patientAccountRepository;

    @Autowired
    protected ScheduleRepository scheduleRepository;

    @Autowired
    protected SessionRepository sessionRepository;

    @Autowired
    protected SlotRepository slotRepository;

    @Autowired
    protected SessionGenerationService sessionGenerationService;

    @Autowired
    protected AppointmentTypeRepository appointmentTypeRepository;

    @Autowired
    protected PatientRepository patientRepository;

    @Autowired
    protected BookingRepository bookingRepository;

    @Autowired
    protected FrontDeskWalkInService frontDeskWalkInService;

    @Autowired
    protected WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    protected WaitlistMatchingService waitlistMatchingService;

    @Autowired
    protected DeVerificationCascadeService deVerificationCascadeService;

    @Autowired
    protected InboxItemRepository inboxItemRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StaffJwtService staffJwtService;

    private int counter = 0;

    @AfterEach
    void cleanDatabase() {
        inboxItemRepository.deleteAll();
        waitlistEntryRepository.deleteAll();
        bookingRepository.deleteAll();
        patientRepository.deleteAll();
        patientAccountRepository.deleteAll();
        appointmentTypeRepository.deleteAll();
        slotRepository.deleteAll();
        sessionRepository.deleteAll();
        scheduleRepository.deleteAll();
        doctorProfileRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        accountRepository.deleteAll();
        clinicRepository.deleteAll();
    }

    protected Clinic saveClinic() {
        counter++;
        Clinic clinic = new Clinic("Clinic " + counter, "1 Test Street", null, null);
        clinic.setVerified(true);
        return clinicRepository.save(clinic);
    }

    protected DoctorProfile saveDoctorProfile() {
        counter++;
        Account account = accountRepository.save(new Account(
                "Dr. Test " + counter, "doctor" + counter + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "DR-" + counter, null));
        DoctorProfile profile = new DoctorProfile(account, "General Medicine", "LIC-" + counter, 5);
        profile.setLicenseVerified(true);
        return doctorProfileRepository.save(profile);
    }

    protected void linkDoctorToClinic(DoctorProfile profile, Clinic clinic, boolean active) {
        RoleAssignment roleAssignment = new RoleAssignment(profile.getAccount(), clinic, RoleAssignment.Role.Doctor);
        if (!active) {
            roleAssignment.deactivate(com.cms.identity.account.domain.RoleAssignment.DeactivationReason.RESIGNED);
        }
        roleAssignmentRepository.save(roleAssignment);
    }

    protected DoctorProfile saveDoctorStaffedAt(Clinic clinic) {
        DoctorProfile profile = saveDoctorProfile();
        linkDoctorToClinic(profile, clinic, true);
        return profile;
    }

    protected String doctorToken(DoctorProfile doctorProfile) {
        return staffJwtService.issueToken(doctorProfile.getAccount().getId());
    }

    protected UUID clinicAdminAccountId(Clinic clinic) {
        counter++;
        Account admin = accountRepository.save(new Account(
                "Admin " + counter, "admin" + counter + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "CA-" + counter, null));
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        return admin.getId();
    }

    protected String clinicAdminToken(Clinic clinic) {
        return staffJwtService.issueToken(clinicAdminAccountId(clinic));
    }

    protected UUID operationsAccountId(Clinic clinic) {
        counter++;
        Account ops = accountRepository.save(new Account(
                "Ops " + counter, "ops" + counter + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "OP-" + counter, null));
        roleAssignmentRepository.save(new RoleAssignment(ops, clinic, RoleAssignment.Role.Operations));
        return ops.getId();
    }

    protected String operationsToken(Clinic clinic) {
        return staffJwtService.issueToken(operationsAccountId(clinic));
    }

    protected PatientAccount savePatientAccount() {
        counter++;
        return patientAccountRepository.save(
                new PatientAccount("patient" + counter + "@example.com", passwordEncoder.encode("Str0ng!Pass"), null));
    }

    /**
     * A Fixed-Time Schedule (every day, 9-13, 15-min) generated into a Session with Slots, for a
     * doctor staffed at the given clinic.
     *
     * <p>065-phase1-stabilization: dated tomorrow, explicitly - a today-dated 09:00 slot is elapsed
     * (unbookable, never offered) for most of the day, which made these fixtures depend on the time
     * the suite runs.
     */
    protected Session saveFixedTimeSessionWithSlots(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.FIXED_TIME, 15));
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        sessionGenerationService.generate(tomorrow);
        return sessionOn(schedule, tomorrow);
    }

    private Session sessionOn(Schedule schedule, LocalDate date) {
        return sessionRepository.findBySchedule_Id(schedule.getId()).stream()
                .filter(s -> s.getSessionDate().equals(date))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No session generated on " + date));
    }

    protected List<Slot> slotsOf(Session session) {
        return slotRepository.findBySession_Id(session.getId());
    }

    protected Slot anOpenSlotOf(Session session) {
        return slotsOf(session).stream()
                .filter(s -> s.getStatus() == SlotStatus.OPEN)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No OPEN Slot in this Session"));
    }

    protected AppointmentType saveAppointmentType(DoctorProfile doctor) {
        return appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
    }

    /**
     * Drives the real production walk-in path, which (as of 038) also creates a WALK_IN InboxItem.
     * 063-front-desk-walk-in: the front-desk registration replaced 025's slot insertion (retired).
     */
    protected Booking insertWalkIn(UUID callerAccountId, Clinic clinic, Session session, AppointmentType appointmentType) {
        return frontDeskWalkInService
                .register(
                        callerAccountId,
                        clinic.getId(),
                        new FrontDeskWalkInService.RegisterInput(
                                session.getId(),
                                null,
                                "Walk-in Patient " + UUID.randomUUID(),
                                "98" + (10000000 + (counter++)),
                                null,
                                appointmentType.getId(),
                                "GENERAL_CHECKUP",
                                null,
                                false))
                .booking();
    }

    protected WaitlistEntry saveWaitingEntry(Clinic clinic, PatientAccount patientAccount, DoctorProfile doctor, Instant joinedAt) {
        WaitlistEntry entry = waitlistEntryRepository.saveAndFlush(new WaitlistEntry(clinic, patientAccount, doctor, null));
        return entry;
    }

    /** Drives the real 031 production path: matching an OPEN Slot against a WAITING entry, which (as of 038) also creates a WAITLIST_OFFER InboxItem. */
    protected void triggerWaitlistOffer(Session session, Slot openSlot) {
        waitlistMatchingService.matchAndOffer(session, openSlot);
    }

    /** Drives the real 008/033 production path: a doctor license revoke cascade, which (as of 038) also creates DEVERIFICATION_CASCADE InboxItem(s). */
    protected void triggerDeverificationCascade(UUID doctorProfileId) {
        deVerificationCascadeService.cascadeFromDoctor(doctorProfileId);
    }

    protected List<InboxItem> outstandingItemsOf(Clinic clinic) {
        return inboxItemRepository.findByClinic_IdAndStatusNotOrderByCreatedAtAsc(
                clinic.getId(), com.cms.inbox.domain.InboxItemStatus.RESOLVED);
    }
}
