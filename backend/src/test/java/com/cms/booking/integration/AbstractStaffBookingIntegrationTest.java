package com.cms.booking.integration;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.repository.DoctorDefaultFeeRepository;
import com.cms.booking.service.StaffBookingService;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.repository.AccountRepository;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.repository.PatientRepository;
import com.cms.scheduling.domain.Schedule;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.repository.ScheduleRepository;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.service.SessionGenerationService;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SlotRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 020: combines 012/013's scheduling fixture with 015's fee-configuration entities, plus Patient/Booking helpers. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractStaffBookingIntegrationTest {

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
    protected StaffBookingService staffBookingService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StaffJwtService staffJwtService;

    private int counter = 0;

    @Autowired
    private BookingAttemptLogRepository bookingAttemptLogRepository;

    @AfterEach
    void cleanDatabase() {
        // 060-booking-abuse-prevention: attempt-log rows reference booking, patient_account and clinic.
        bookingAttemptLogRepository.deleteAll();
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

    protected String operationsToken(Clinic clinic) {
        counter++;
        Account ops = accountRepository.save(new Account(
                "Ops " + counter, "ops" + counter + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "OP-" + counter, null));
        roleAssignmentRepository.save(new RoleAssignment(ops, clinic, RoleAssignment.Role.Operations));
        return staffJwtService.issueToken(ops.getId());
    }

    protected String unrelatedStaffToken() {
        return clinicAdminToken(saveClinic());
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

    protected Slot anOpenSlotOf(Session session) {
        return slotRepository.findBySession_Id(session.getId()).get(0);
    }

    protected AppointmentType saveAppointmentTypeWithOverride(DoctorProfile doctor, BigDecimal feeOverride) {
        return appointmentTypeRepository.save(new AppointmentType(doctor, "Follow-up", feeOverride));
    }

    protected AppointmentType saveAppointmentTypeWithNoOverride(DoctorProfile doctor) {
        return appointmentTypeRepository.save(new AppointmentType(doctor, "New Patient", null));
    }

    /** Uses a random suffix (not the shared {@code counter}) so it's safe to call from concurrent test threads. */
    protected Patient saveExistingPatient(Clinic clinic) {
        return patientRepository.save(
                new Patient(clinic, null, "Existing Patient " + java.util.UUID.randomUUID(), null));
    }
}
