package com.cms.discovery.integration;

import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
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

/**
 * 035: shared fixtures for public discovery search integration tests. Mirrors
 * {@code identity.admin.integration.AbstractAdminIntegrationTest}'s clinic/account/
 * doctor-profile/role-assignment builders, minus any admin auth concern - this endpoint
 * has none.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractDiscoveryIntegrationTest {

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
    protected PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanDatabase() {
        doctorProfileRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        accountRepository.deleteAll();
        clinicRepository.deleteAll();
    }

    protected Clinic saveClinic(String name, boolean verified) {
        return saveClinic(name, "1 Test Street", verified);
    }

    protected Clinic saveClinic(String name, String address, boolean verified) {
        return saveClinic(name, address, null, verified);
    }

    /** patient-search-advanced-filtering: lets a test give a clinic a real city, for the new city-filter tests. */
    protected Clinic saveClinic(String name, String address, String city, boolean verified) {
        Clinic clinic = new Clinic(name, address, city, null, null);
        clinic.setVerified(verified);
        return clinicRepository.save(clinic);
    }

    protected DoctorProfile saveDoctorProfile(
            String doctorName, String licenseNumber, String specialization, boolean licenseVerified, boolean visible) {
        return saveDoctorProfile(doctorName, licenseNumber, specialization, 5, licenseVerified, visible);
    }

    /** patient-search-advanced-filtering: lets a test give a doctor a real experienceYears value, for the new experience-filter/sort tests. */
    protected DoctorProfile saveDoctorProfile(
            String doctorName,
            String licenseNumber,
            String specialization,
            int experienceYears,
            boolean licenseVerified,
            boolean visible) {
        Account account = accountRepository.save(new Account(
                doctorName, licenseNumber + "@example.com", passwordEncoder.encode("Str0ng!Pass"),
                "DR-" + licenseNumber, null));
        DoctorProfile profile = new DoctorProfile(account, specialization, licenseNumber, experienceYears);
        profile.setLicenseVerified(licenseVerified);
        profile.setVisible(visible);
        return doctorProfileRepository.save(profile);
    }

    /** Convenience overload for tests that don't care about the doctor's display name/specialization. */
    protected DoctorProfile saveDoctorProfile(String licenseNumber, boolean licenseVerified, boolean visible) {
        return saveDoctorProfile("Dr. Test", licenseNumber, "General Medicine", licenseVerified, visible);
    }

    protected RoleAssignment linkDoctorToClinic(DoctorProfile profile, Clinic clinic, boolean active) {
        RoleAssignment roleAssignment = new RoleAssignment(profile.getAccount(), clinic, RoleAssignment.Role.Doctor);
        if (!active) {
            roleAssignment.deactivate(com.cms.identity.account.RoleAssignment.DeactivationReason.RESIGNED);
        }
        return roleAssignmentRepository.save(roleAssignment);
    }
}
