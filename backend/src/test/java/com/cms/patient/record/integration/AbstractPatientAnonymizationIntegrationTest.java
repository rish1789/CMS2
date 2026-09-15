package com.cms.patient.record.integration;

import com.cms.booking.AppointmentType;
import com.cms.booking.AppointmentTypeRepository;
import com.cms.booking.Booking;
import com.cms.booking.BookingRepository;
import com.cms.clinical.ConsultationNoteRepository;
import com.cms.clinical.ConsultationNoteService;
import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.StaffJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.patient.account.PatientAccount;
import com.cms.patient.account.PatientAccountRepository;
import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientAnonymizationService;
import com.cms.patient.record.PatientRepository;
import com.cms.scheduling.Schedule;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.ScheduleRepository;
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

/** 037: clinic/doctor/staff-token(Operations, ClinicAdmin, Doctor)/patient/booking fixture, mirroring this codebase's established fixture shape. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractPatientAnonymizationIntegrationTest {

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
    protected PatientRepository patientRepository;

    @Autowired
    protected PatientAccountRepository patientAccountRepository;

    @Autowired
    protected BookingRepository bookingRepository;

    @Autowired
    protected PatientAnonymizationService patientAnonymizationService;

    @Autowired
    protected ConsultationNoteRepository consultationNoteRepository;

    @Autowired
    protected ConsultationNoteService consultationNoteService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StaffJwtService staffJwtService;

    private int counter = 0;

    @AfterEach
    void cleanDatabase() {
        consultationNoteRepository.deleteAll();
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
        Account operations = accountRepository.save(new Account(
                "Operations " + counter, "ops" + counter + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "OP-" + counter, null));
        roleAssignmentRepository.save(new RoleAssignment(operations, clinic, RoleAssignment.Role.Operations));
        return staffJwtService.issueToken(operations.getId());
    }

    protected PatientAccount savePatientAccount() {
        counter++;
        return patientAccountRepository.save(
                new PatientAccount("patient" + counter + "@example.com", passwordEncoder.encode("Str0ng!Pass"), null));
    }

    protected Patient savePatient(Clinic clinic, PatientAccount patientAccountOrNull) {
        counter++;
        return patientRepository.save(
                new Patient(clinic, patientAccountOrNull, "Test Patient " + counter, "9999900000" + counter));
    }

    /** A Fixed-Time Schedule (every day, 9-13, 15-min) generated into a Session with Slots, for a doctor staffed at the given clinic. */
    protected Session saveFixedTimeSessionWithSlots(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.FIXED_TIME, 15));
        sessionGenerationService.generate(LocalDate.now());
        return sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
    }

    /** Books the given already-generated Slot for the given Patient, creating an AppointmentType/fee, and flips the Slot BOOKED. */
    protected Booking bookSlotForPatient(DoctorProfile doctor, Slot slot, Patient patient) {
        AppointmentType appointmentType =
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Booking booking = bookingRepository.saveAndFlush(
                new Booking(slot, patient, appointmentType, new BigDecimal("300.00"), doctor.getAccount().getId()));
        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);
        return booking;
    }
}
