package com.cms.booking.integration;

import com.cms.booking.AppointmentType;
import com.cms.booking.AppointmentTypeRepository;
import com.cms.booking.Booking;
import com.cms.booking.BookingRepository;
import com.cms.booking.DoctorDefaultFeeRepository;
import com.cms.booking.WalkInInsertionService;
import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.StaffJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientRepository;
import com.cms.scheduling.Schedule;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.ScheduleRepository;
import com.cms.scheduling.QueueSlotService;
import com.cms.scheduling.Session;
import com.cms.scheduling.SessionGenerationService;
import com.cms.scheduling.SessionRepository;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import com.cms.scheduling.SlotStatus;
import java.math.BigDecimal;
import java.time.DayOfWeek;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 025: duplicates {@code AbstractStaffBookingIntegrationTest}'s fixture shape rather than
 * extending it (no shared base class between staff-booking test fixtures anywhere in this
 * codebase - established precedent), plus three new helpers for the priority-tier candidates
 * this feature's own tests need.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractWalkInIntegrationTest {

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
    protected ScheduleRepository scheduleRepository;

    @Autowired
    protected SessionRepository sessionRepository;

    @Autowired
    protected SlotRepository slotRepository;

    @Autowired
    protected SessionGenerationService sessionGenerationService;

    @Autowired
    protected QueueSlotService queueSlotService;

    @Autowired
    protected AppointmentTypeRepository appointmentTypeRepository;

    @Autowired
    protected DoctorDefaultFeeRepository doctorDefaultFeeRepository;

    @Autowired
    protected PatientRepository patientRepository;

    @Autowired
    protected BookingRepository bookingRepository;

    @Autowired
    protected WalkInInsertionService walkInInsertionService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StaffJwtService staffJwtService;

    private int counter = 0;

    @AfterEach
    void cleanDatabase() {
        bookingRepository.deleteAll();
        patientRepository.deleteAll();
        doctorDefaultFeeRepository.deleteAll();
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
            roleAssignment.deactivate(com.cms.identity.account.RoleAssignment.DeactivationReason.RESIGNED);
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

    protected String clinicAdminToken(Clinic clinic) {
        counter++;
        Account admin = accountRepository.save(new Account(
                "Admin " + counter, "admin" + counter + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "CA-" + counter, null));
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        return staffJwtService.issueToken(admin.getId());
    }

    protected String operationsToken(Clinic clinic) {
        counter++;
        Account ops = accountRepository.save(new Account(
                "Ops " + counter, "ops" + counter + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "OP-" + counter, null));
        roleAssignmentRepository.save(new RoleAssignment(ops, clinic, RoleAssignment.Role.Operations));
        return staffJwtService.issueToken(ops.getId());
    }

    /** A Fixed-Time Schedule (every day, 9-13, 15-min) generated into a Session with Slots (including 1 cold-start buffer Slot - 012/022), for a doctor staffed at the given clinic. */
    protected Session saveFixedTimeSessionWithSlots(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.FIXED_TIME, 15));
        sessionGenerationService.generate(LocalDate.now());
        return sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
    }

    /** 025 convergence: a Queue-mode Session, for asserting walk-in insertion rejects non-Fixed-Time sessions. */
    protected Session saveQueueSession(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class), LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.QUEUE, null));
        sessionGenerationService.generate(LocalDate.now());
        return sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
    }

    protected List<Slot> slotsOf(Session session) {
        return slotRepository.findBySession_Id(session.getId());
    }

    /** The Session's cold-start-generated OPEN buffer Slot (012/022 - always exactly one, no no-show data seeded here). */
    protected Slot aBufferSlotOf(Session session) {
        return slotsOf(session).stream()
                .filter(Slot::isBuffer)
                .filter(s -> s.getStatus() == SlotStatus.OPEN)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No OPEN buffer Slot generated for this Session"));
    }

    /** Any non-buffer OPEN regular Slot in the Session. */
    protected Slot aRegularOpenSlotOf(Session session) {
        return slotsOf(session).stream()
                .filter(s -> !s.isBuffer())
                .filter(s -> s.getStatus() == SlotStatus.OPEN)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No OPEN regular Slot in this Session"));
    }

    /**
     * Manufactures a priority-(2) candidate directly (bypassing the real 021 sweep, which
     * this feature only reads the resulting state of): takes a regular OPEN Slot, books it,
     * then marks it NO_SHOW. Returns the Slot; the still-present old Booking is reachable via
     * {@code bookingRepository.findBySlot_Id(slot.getId())}.
     */
    protected Slot aNoShowSlotWithOldBooking(
            Session session, Patient patient, AppointmentType appointmentType, BigDecimal fee) {
        Slot slot = aRegularOpenSlotOf(session);
        // bookedByAccountId has an FK to account - the doctor's own account is a convenient
        // always-valid one for this fixture (who "booked" the original no-show visit is
        // irrelevant to any test using this helper).
        UUID bookedByAccountId = session.getDoctorProfile().getAccount().getId();
        bookingRepository.saveAndFlush(new Booking(slot, patient, appointmentType, fee, bookedByAccountId));
        slot.setStatus(SlotStatus.NO_SHOW);
        return slotRepository.saveAndFlush(slot);
    }

    protected AppointmentType saveAppointmentTypeWithOverride(DoctorProfile doctor, BigDecimal feeOverride) {
        return appointmentTypeRepository.save(new AppointmentType(doctor, "Follow-up", feeOverride));
    }

    protected AppointmentType saveAppointmentTypeWithNoOverride(DoctorProfile doctor) {
        return appointmentTypeRepository.save(new AppointmentType(doctor, "New Patient", null));
    }

    /** Uses a random suffix (not the shared {@code counter}) so it's safe to call from concurrent test threads. */
    protected Patient saveExistingPatient(Clinic clinic) {
        return patientRepository.save(new Patient(clinic, null, "Existing Patient " + UUID.randomUUID(), null));
    }
}
