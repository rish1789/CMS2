package com.cms.booking.integration;

import com.cms.booking.AppointmentType;
import com.cms.booking.AppointmentTypeRepository;
import com.cms.booking.Booking;
import com.cms.booking.BookingRepository;
import com.cms.booking.DoctorDefaultFeeRepository;
import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.StaffJwtService;
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
import com.cms.scheduling.SessionRepository;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import com.cms.scheduling.SlotStatus;
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

/**
 * 028: combines 020's staff-token fixture shape with 022's Patient Account fixture (mirroring
 * {@code AbstractQueueBookingIntegrationTest}'s own established combination pattern - a fresh,
 * duplicated fixture, not an inheritance chain, per this module's precedent), plus a helper that
 * builds a confirmed Fixed-Time Booking at an explicit scheduled time relative to "now"
 * (mirrors 023's {@code AbstractNoShowDetectionIntegrationTest.saveFixedTimeSlotAt}, extended to
 * also create the Booking itself, since 2-hour-cutoff testing needs real relative-to-now times -
 * unlike the fixed 2026-09-03 date {@code saveFixedTimeSessionWithSlots} elsewhere uses).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractBookingCancellationIntegrationTest {

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
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StaffJwtService staffJwtService;

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

    protected String patientToken(PatientAccount account) {
        return patientJwtService.issueToken(account.getId());
    }

    /** A Fixed-Time Session (today) + a Slot at the given scheduled LocalTime, mirrors 023's identical helper. */
    private Slot saveFixedTimeSlotAt(Clinic clinic, DoctorProfile doctor, LocalTime scheduledTime) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(0, 0), LocalTime.of(23, 59), ScheduleMode.FIXED_TIME, 15));
        Session session = sessionRepository.save(new Session(
                schedule, clinic, doctor, LocalDate.now(), ScheduleMode.FIXED_TIME, scheduledTime, scheduledTime.plusMinutes(15), 15));
        return slotRepository.save(new Slot(session, scheduledTime, scheduledTime.plusMinutes(15), false));
    }

    /** A confirmed (BOOKED Slot, ACTIVE Booking) Fixed-Time appointment at the given scheduled time relative to now - for cutoff testing. */
    protected Booking saveConfirmedBookingAt(Clinic clinic, DoctorProfile doctor, PatientAccount patientAccount, LocalTime scheduledTime) {
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, scheduledTime);
        AppointmentType appointmentType = appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Patient patient = patientRepository.save(new Patient(clinic, patientAccount, "Test Patient " + UUID.randomUUID(), null));
        Booking booking = bookingRepository.saveAndFlush(
                new Booking(slot, patient, appointmentType, new BigDecimal("300.00"), doctor.getAccount().getId()));
        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);
        return booking;
    }

    /** Same as above, for a walk-in-style Booking with no linked Patient Account (staff-only cancellation scenarios). */
    protected Booking saveConfirmedBookingAt(Clinic clinic, DoctorProfile doctor, LocalTime scheduledTime) {
        return saveConfirmedBookingAt(clinic, doctor, null, scheduledTime);
    }
}
