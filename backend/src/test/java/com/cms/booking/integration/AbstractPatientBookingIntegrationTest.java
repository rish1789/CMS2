package com.cms.booking.integration;

import com.cms.booking.AppointmentType;
import com.cms.booking.AppointmentTypeRepository;
import com.cms.booking.BookingRepository;
import com.cms.booking.DoctorDefaultFeeRepository;
import com.cms.booking.PatientBookingService;
import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.patient.account.JwtService;
import com.cms.patient.account.PatientAccount;
import com.cms.patient.account.PatientAccountRepository;
import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientRepository;
import com.cms.scheduling.Schedule;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.ScheduleRepository;
import com.cms.scheduling.Session;
import com.cms.scheduling.SessionGenerationService;
import com.cms.scheduling.SessionRepository;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
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

/** 021: combines 012/015/016's scheduling/fee-configuration fixture with a Patient Account signup+token helper. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractPatientBookingIntegrationTest {

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
    protected PatientBookingService patientBookingService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService patientJwtService;

    private int counter = 0;

    @AfterEach
    void cleanDatabase() {
        bookingRepository.deleteAll();
        patientRepository.deleteAll();
        patientAccountRepository.deleteAll();
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

    /**
     * A Fixed-Time Schedule (every day, 9-13, 15-min) generated into a Session with Slots, for a
     * doctor staffed at the given clinic. patient-slot-booking-date-logic: generates starting
     * from {@code LocalDate.now()}, not a hardcoded date - {@code PatientBookingService
     * .listOpenSlots}/{@code bookSlot} now floor to today-or-later, so a fixed past-tense date
     * here would silently stop producing any bookable fixture the moment "today" passed it.
     */
    protected Session saveFixedTimeSessionWithSlots(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.FIXED_TIME, 15));
        sessionGenerationService.generate(LocalDate.now());
        return sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
    }

    protected Slot anOpenSlotOf(Session session) {
        return slotRepository.findBySession_Id(session.getId()).get(0);
    }

    /**
     * patient-slot-booking-date-logic: a Fixed-Time Session/Slot dated yesterday - built by
     * hand (never via {@code sessionGenerationService.generate}, which can only ever generate
     * a forward-looking window from its run date) to prove the "hidden or disabled" floor
     * actually excludes an already-past day, not just one that happens to never come up.
     */
    protected Slot savePastDatedOpenFixedTimeSlot(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.FIXED_TIME, 15));
        Session session = sessionRepository.save(new Session(
                schedule, clinic, doctor, LocalDate.now().minusDays(1), ScheduleMode.FIXED_TIME,
                LocalTime.of(9, 0), LocalTime.of(9, 15), 15));
        return slotRepository.save(new Slot(session, LocalTime.of(9, 0), LocalTime.of(9, 15), false));
    }

    /** A Queue-mode Session with one manually-created OPEN token Slot (simulating QueueSlotService's on-demand creation), for asserting the patient-listing query excludes it. */
    protected Session saveQueueSessionWithOneOpenSlot(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class), LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.QUEUE, null));
        sessionGenerationService.generate(LocalDate.now());
        Session session = sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
        slotRepository.save(new Slot(session, 1));
        return session;
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

    /** Uses a random suffix so it's safe to call from concurrent test threads. */
    protected Patient saveWalkInPatientWithPhone(Clinic clinic, String phone) {
        return patientRepository.save(new Patient(clinic, null, "Walk-in " + UUID.randomUUID(), phone));
    }

    protected PatientAccount savePatientAccount() {
        return savePatientAccount(null);
    }

    protected PatientAccount savePatientAccount(String mobile) {
        counter++;
        return patientAccountRepository.save(
                new PatientAccount("patient" + counter + "@example.com", passwordEncoder.encode("Str0ng!Pass"), mobile));
    }

    protected String patientToken(PatientAccount account) {
        return patientJwtService.issueToken(account.getId());
    }

    /** A syntactically valid Indian mobile number, unique per call. */
    protected String aValidMobileNumber() {
        counter++;
        return "9" + String.format("%09d", counter);
    }
}
