package com.cms.waitlist.integration;

import com.cms.booking.AppointmentType;
import com.cms.booking.AppointmentTypeRepository;
import com.cms.booking.Booking;
import com.cms.booking.BookingCancellationService;
import com.cms.booking.BookingRepository;
import com.cms.booking.SessionCancellationService;
import com.cms.booking.SessionPartialCancellationService;
import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.StaffJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.notification.NotificationEventRepository;
import com.cms.patient.account.JwtService;
import com.cms.patient.account.PatientAccount;
import com.cms.patient.account.PatientAccountRepository;
import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientRepository;
import com.cms.scheduling.NoShowDetectionService;
import com.cms.scheduling.Schedule;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.ScheduleRepository;
import com.cms.scheduling.Session;
import com.cms.scheduling.SessionGenerationService;
import com.cms.scheduling.SessionRepository;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import com.cms.scheduling.SlotStatus;
import com.cms.waitlist.WaitlistClaimService;
import com.cms.waitlist.WaitlistEntry;
import com.cms.waitlist.WaitlistEntryRepository;
import com.cms.waitlist.WaitlistEntryStatus;
import com.cms.waitlist.WaitlistExpirySweepService;
import com.cms.waitlist.WaitlistReleaseService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 031: clinic/doctor/staff-token/patient-account-token fixture, mirroring
 * {@code AbstractSessionCancellationIntegrationTest}'s combination shape. {@code
 * WaitlistEntry.joinedAt} has no production setter (it's stamped at construction, not meant
 * to be mutable) - {@link #saveWaitlistEntry} backdates it via a direct SQL update, test-only,
 * mirroring 030's identical {@code addQueueSlotWithCreatedAt} pattern.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractWaitlistIntegrationTest {

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
    protected WaitlistEntryRepository waitlistEntryRepository;

    @Autowired
    protected NotificationEventRepository notificationEventRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StaffJwtService staffJwtService;

    @Autowired
    protected JwtService patientJwtService;

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
    protected BookingCancellationService bookingCancellationService;

    @Autowired
    protected SessionCancellationService sessionCancellationService;

    @Autowired
    protected SessionPartialCancellationService sessionPartialCancellationService;

    @Autowired
    protected NoShowDetectionService noShowDetectionService;

    @Autowired
    protected WaitlistClaimService waitlistClaimService;

    @Autowired
    protected WaitlistReleaseService waitlistReleaseService;

    @Autowired
    protected WaitlistExpirySweepService waitlistExpirySweepService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private int counter = 0;

    @AfterEach
    void cleanDatabase() {
        notificationEventRepository.deleteAll();
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

    protected DoctorProfile saveDoctorProfile(String specialization) {
        counter++;
        Account account = accountRepository.save(new Account(
                "Dr. Test " + counter, "doctor" + counter + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "DR-" + counter, null));
        DoctorProfile profile = new DoctorProfile(account, specialization, "LIC-" + counter, 5);
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

    protected DoctorProfile saveDoctorStaffedAt(Clinic clinic, String specialization) {
        DoctorProfile profile = saveDoctorProfile(specialization);
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

    protected String unrelatedStaffToken() {
        return clinicAdminToken(saveClinic());
    }

    protected PatientAccount savePatientAccount() {
        counter++;
        return patientAccountRepository.save(
                new PatientAccount("patient" + counter + "@example.com", passwordEncoder.encode("Str0ng!Pass"), null));
    }

    protected String patientToken(PatientAccount patientAccount) {
        return patientJwtService.issueToken(patientAccount.getId());
    }

    /** Constructs a WaitlistEntry, then backdates joinedAt for precise longest-waiting-within-a-tier scenarios. */
    protected WaitlistEntry saveWaitlistEntry(
            Clinic clinic,
            PatientAccount patientAccount,
            DoctorProfile doctorProfileOrNull,
            String specializationOrNull,
            Instant joinedAt) {
        WaitlistEntry entry = waitlistEntryRepository.saveAndFlush(
                new WaitlistEntry(clinic, patientAccount, doctorProfileOrNull, specializationOrNull));
        jdbcTemplate.update("UPDATE waitlist_entry SET joined_at = ? WHERE id = ?", Timestamp.from(joinedAt), entry.getId());
        entityManager.refresh(entry);
        return entry;
    }

    /**
     * A Fixed-Time Schedule (every day, 9-13, 15-min) generated into a Session with Slots, for a
     * doctor staffed at the given clinic - dated in the past (not just "not a fixed calendar
     * date") so 021's no-show sweep ({@code WaitlistMatchingExclusivityTest
     * .noShowReleaseNeverBumpsTheWaitlist}) can fire deterministically: {@code
     * NoShowDetectionService.detectAndMarkNoShows} only marks a Slot whose {@code
     * sessionDate}+{@code startTime}, plus a 10-minute grace period, is already before {@code
     * LocalDateTime.now()} - generating from today (or later) would make that condition depend
     * on what time of day the test happens to run, not just what day it is.
     */
    protected Session saveFixedTimeSessionWithSlots(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.FIXED_TIME, 15));
        sessionGenerationService.generate(LocalDate.now().minusDays(1));
        return sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
    }

    /**
     * 032: constructs an entry already in OFFERED state for a specific Slot, with a
     * controllable (possibly already-lapsed) window - via a direct SQL update, test-only,
     * mirroring {@link #saveWaitlistEntry}'s identical backdating pattern.
     */
    protected WaitlistEntry saveOfferedWaitlistEntry(
            Clinic clinic,
            PatientAccount patientAccount,
            DoctorProfile doctorProfileOrNull,
            String specializationOrNull,
            Slot offeredSlot,
            Instant offeredAt,
            Instant offerExpiresAt) {
        WaitlistEntry entry = waitlistEntryRepository.saveAndFlush(
                new WaitlistEntry(clinic, patientAccount, doctorProfileOrNull, specializationOrNull));
        jdbcTemplate.update(
                "UPDATE waitlist_entry SET status = ?, offered_slot_id = ?, offered_at = ?, offer_expires_at = ? WHERE id = ?",
                WaitlistEntryStatus.OFFERED.name(),
                offeredSlot.getId(),
                Timestamp.from(offeredAt),
                Timestamp.from(offerExpiresAt),
                entry.getId());
        entityManager.refresh(entry);
        return entry;
    }

    /** Backdates an already-OFFERED entry's window past expiry, for multi-hop cascade tests that need to lapse a specific entry mid-test. */
    protected void jdbcTemplateUpdateOfferExpiresAt(UUID entryId, Instant offerExpiresAt) {
        jdbcTemplate.update(
                "UPDATE waitlist_entry SET offer_expires_at = ? WHERE id = ?", Timestamp.from(offerExpiresAt), entryId);
    }

    /** Books the given already-generated Slot: creates an AppointmentType/fee, a Patient linked to patientAccount, a Booking, and flips the Slot BOOKED. */
    protected Booking bookSlot(Clinic clinic, DoctorProfile doctor, Slot slot, PatientAccount patientAccount) {
        AppointmentType appointmentType =
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Patient patient =
                patientRepository.save(new Patient(clinic, patientAccount, "Test Patient " + UUID.randomUUID(), null));
        Booking booking = bookingRepository.saveAndFlush(
                new Booking(slot, patient, appointmentType, new BigDecimal("300.00"), doctor.getAccount().getId()));
        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);
        return booking;
    }
}
