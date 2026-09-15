package com.cms.clinical.integration;

import com.cms.booking.AppointmentType;
import com.cms.booking.AppointmentTypeRepository;
import com.cms.booking.Booking;
import com.cms.booking.BookingRepository;
import com.cms.clinical.ConsultationNoteRepository;
import com.cms.clinical.ConsultationNoteService;
import com.cms.clinical.ExternalRecordReferenceRepository;
import com.cms.clinical.ExternalRecordReferenceService;
import com.cms.clinical.PrescriptionRepository;
import com.cms.clinical.PrescriptionService;
import com.cms.clinical.RetentionPurgeService;
import com.cms.clinical.dto.PrescriptionItemRequest;
import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.StaffJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
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
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
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

/** 038: clinic/doctor/patient/booking fixture plus content-creation and backdating helpers, mirroring this codebase's established fixture shape (037's own AbstractPatientAnonymizationIntegrationTest). */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractRetentionPurgeIntegrationTest {

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
    protected PrescriptionRepository prescriptionRepository;

    @Autowired
    protected PrescriptionService prescriptionService;

    @Autowired
    protected ExternalRecordReferenceRepository externalRecordReferenceRepository;

    @Autowired
    protected ExternalRecordReferenceService externalRecordReferenceService;

    @Autowired
    protected RetentionPurgeService retentionPurgeService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StaffJwtService staffJwtService;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    private int counter = 0;

    @AfterEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM prescription_item");
        prescriptionRepository.deleteAll();
        externalRecordReferenceRepository.deleteAll();
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

    protected DoctorProfile saveDoctorStaffedAt(Clinic clinic) {
        counter++;
        Account account = accountRepository.save(new Account(
                "Dr. Test " + counter, "doctor" + counter + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "DR-" + counter, null));
        DoctorProfile profile = new DoctorProfile(account, "General Medicine", "LIC-" + counter, 5);
        profile.setLicenseVerified(true);
        DoctorProfile saved = doctorProfileRepository.save(profile);
        roleAssignmentRepository.save(new RoleAssignment(account, clinic, RoleAssignment.Role.Doctor));
        return saved;
    }

    protected Patient savePatient(Clinic clinic) {
        counter++;
        return patientRepository.save(new Patient(clinic, null, "Test Patient " + counter, "9999900000" + counter));
    }

    /** A Fixed-Time Schedule (every day, 9-13, 15-min) generated into a Session with Slots, for a doctor staffed at the given clinic. */
    protected Session saveFixedTimeSessionWithSlots(Clinic clinic, DoctorProfile doctor) {
        Schedule schedule = scheduleRepository.save(new Schedule(
                doctor, clinic, EnumSet.allOf(DayOfWeek.class),
                LocalTime.of(9, 0), LocalTime.of(13, 0), ScheduleMode.FIXED_TIME, 15));
        sessionGenerationService.generate(LocalDate.now());
        return sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
    }

    /** Books the given already-generated Slot for the given Patient, then backdates the Booking's {@code created_at} directly via SQL - no API path exists to create a 3-year-old booking, so this is the test-only device for it (mirrors AbstractPrescriptionIntegrationTest's own use of JdbcTemplate). */
    protected Booking bookSlotWithCreatedAt(DoctorProfile doctor, Slot slot, Patient patient, Instant createdAt) {
        AppointmentType appointmentType =
                appointmentTypeRepository.save(new AppointmentType(doctor, "Consultation", new BigDecimal("300.00")));
        Booking booking = bookingRepository.saveAndFlush(
                new Booking(slot, patient, appointmentType, new BigDecimal("300.00"), doctor.getAccount().getId()));
        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);
        jdbcTemplate.update("UPDATE booking SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), booking.getId());
        return booking;
    }

    protected Instant threeYearsAndOneDayAgo() {
        return Instant.now().minusSeconds(3L * 365 * 24 * 3600 + 24 * 3600);
    }

    protected Instant oneYearAgo() {
        return Instant.now().minusSeconds(365L * 24 * 3600);
    }

    protected void anonymize(Clinic clinic, Patient patient) {
        patientAnonymizationService.anonymize(clinic.getId(), patient.getId());
    }

    protected void attachConsultationNote(Clinic clinic, Booking booking, DoctorProfile doctor) {
        consultationNoteService.create(clinic.getId(), booking.getId(), doctor.getAccount().getId(), "Visit note.");
    }

    protected void attachPrescription(Clinic clinic, Booking booking, DoctorProfile doctor) {
        prescriptionService.create(
                clinic.getId(),
                booking.getId(),
                doctor.getAccount().getId(),
                List.of(new PrescriptionItemRequest("Paracetamol", "500mg", "Twice daily", "5 days", "After food")));
    }

    protected void attachExternalRecordReference(Clinic clinic, Booking booking, DoctorProfile doctor) {
        externalRecordReferenceService.create(
                clinic.getId(),
                booking.getId(),
                doctor.getAccount().getId(),
                "Lab Report",
                "External Lab",
                LocalDate.of(2023, 1, 1),
                "Routine bloodwork.");
    }
}
