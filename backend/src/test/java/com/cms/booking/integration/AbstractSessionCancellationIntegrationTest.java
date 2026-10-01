package com.cms.booking.integration;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.domain.Booking;
import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.repository.DoctorDefaultFeeRepository;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.repository.AccountRepository;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.notification.repository.NotificationEventRepository;
import com.cms.notification.service.NotificationEventService;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.account.repository.PatientAccountRepository;
import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.repository.PatientRepository;
import com.cms.scheduling.domain.Schedule;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.repository.ScheduleRepository;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.service.SessionGenerationService;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SessionCancellationRepository;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.domain.SlotStatus;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 029: combines 020/022's Fixed-Time and Queue-mode Session-generation fixtures with 028's
 * cancellation-focused Patient Account fixture (this codebase's established
 * {@code com.cms.booking} test-fixture duplication pattern), plus one new helper -
 * {@code bookSlot} - that books a specific already-generated Slot against an optional Patient
 * Account, for building the precise mixed-Slot-state scenarios this feature's own tests need.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractSessionCancellationIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("cms_test");

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
    protected SessionCancellationRepository sessionCancellationRepository;

    @Autowired
    protected SessionGenerationService sessionGenerationService;

    @Autowired
    protected AppointmentTypeRepository appointmentTypeRepository;

    @Autowired
    protected DoctorDefaultFeeRepository doctorDefaultFeeRepository;

    @Autowired
    protected PatientRepository patientRepository;

    @Autowired
    protected BookingRepository bookingRepository;

    @Autowired
    protected PatientAccountRepository patientAccountRepository;

    @Autowired
    protected NotificationEventRepository notificationEventRepository;

    @Autowired
    protected NotificationEventService notificationEventService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StaffJwtService staffJwtService;

    private int counter = 0;

    @Autowired
    private BookingAttemptLogRepository bookingAttemptLogRepository;

    @AfterEach
    void cleanDatabase() {
        notificationEventRepository.deleteAll();
        // 060-booking-abuse-prevention: attempt-log rows reference booking, patient_account and clinic.
        bookingAttemptLogRepository.deleteAll();
        bookingRepository.deleteAll();
        patientRepository.deleteAll();
        patientAccountRepository.deleteAll();
        doctorDefaultFeeRepository.deleteAll();
        appointmentTypeRepository.deleteAll();
        slotRepository.deleteAll();
        // 065-phase1-stabilization: cancellation records reference session and account (no cascade).
        sessionCancellationRepository.deleteAll();
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

    /** A Fixed-Time Schedule (every day, 9-13, 15-min) generated into a Session with Slots, for a doctor staffed at the given clinic. */
    protected Session saveFixedTimeSessionWithSlots(Clinic clinic, DoctorProfile doctor) {
        return saveFixedTimeSessionWithSlotsOn(clinic, doctor, LocalDate.now());
    }

    /**
     * The same fixture on a given date. A test that needs its slots to still be bookable (e.g. a
     * waitlist offer, which 065's availability rule refuses for an already-started slot) must use
     * a future date - today's 9:00 slots have elapsed for any run after 9:00.
     */
    protected Session saveFixedTimeSessionWithSlotsOn(Clinic clinic, DoctorProfile doctor, LocalDate date) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.FIXED_TIME, 15));
        sessionGenerationService.generate(date);
        return sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
    }

    /** An active Queue/Token Session (every day) for a doctor staffed at the given clinic - no Slots pre-generated (013/019 mint on demand). */
    protected Session saveQueueSession(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class), LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.QUEUE, null));
        sessionGenerationService.generate(LocalDate.now());
        return sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
    }

    protected Slot addQueueSlot(Session session, int tokenNumber) {
        return slotRepository.save(new Slot(session, tokenNumber));
    }

    /** Books the given already-generated Slot: creates an AppointmentType/fee, a Patient (linked to patientAccount if given, else a walk-in), a Booking, and flips the Slot BOOKED. */
    protected Booking bookSlot(Clinic clinic, DoctorProfile doctor, Slot slot, PatientAccount patientAccountOrNull) {
        AppointmentType appointmentType = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Patient patient = patientRepository.save(
                new Patient(clinic, patientAccountOrNull, "Test Patient " + UUID.randomUUID(), null));
        Booking booking = bookingRepository.saveAndFlush(
                new Booking(slot, patient, appointmentType, new BigDecimal("300.00"), doctor.getAccount().getId()));
        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);
        return booking;
    }

    protected Booking bookSlot(Clinic clinic, DoctorProfile doctor, Slot slot) {
        return bookSlot(clinic, doctor, slot, null);
    }
}
