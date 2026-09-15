package com.cms.booking.integration;

import com.cms.booking.AppointmentTypeRepository;
import com.cms.booking.AppointmentTypeService;
import com.cms.booking.DoctorDefaultFeeRepository;
import com.cms.booking.FeeResolutionService;
import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.StaffJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
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

/** 017: shared fixtures, mirroring AbstractScheduleIntegrationTest's clinic/account/doctor/token-issuing pattern. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractBookingIntegrationTest {

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
    protected AppointmentTypeRepository appointmentTypeRepository;

    @Autowired
    protected DoctorDefaultFeeRepository doctorDefaultFeeRepository;

    @Autowired
    protected FeeResolutionService feeResolutionService;

    @Autowired
    protected AppointmentTypeService appointmentTypeService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StaffJwtService staffJwtService;

    private int counter = 0;

    @AfterEach
    void cleanDatabase() {
        doctorDefaultFeeRepository.deleteAll();
        appointmentTypeRepository.deleteAll();
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

    protected RoleAssignment linkDoctorToClinic(DoctorProfile profile, Clinic clinic, boolean active) {
        RoleAssignment roleAssignment = new RoleAssignment(profile.getAccount(), clinic, RoleAssignment.Role.Doctor);
        if (!active) {
            roleAssignment.deactivate(com.cms.identity.account.RoleAssignment.DeactivationReason.RESIGNED);
        }
        return roleAssignmentRepository.save(roleAssignment);
    }

    /** A verified Clinic with a Doctor Profile actively assigned to it. */
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

    /** A token belonging to neither the target doctor nor an active ClinicAdmin at any of that doctor's clinics. */
    protected String unrelatedStaffToken() {
        return clinicAdminToken(saveClinic());
    }
}
