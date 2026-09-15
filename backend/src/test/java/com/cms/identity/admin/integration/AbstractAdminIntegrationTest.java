package com.cms.identity.admin.integration;

import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.admin.SuperAdminJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractAdminIntegrationTest {

    protected static final String SUPER_ADMIN_USERNAME = "test-super-admin";
    protected static final String SUPER_ADMIN_PASSWORD = "Str0ng!Pass";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("cms_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("admin.super-admin.username", () -> SUPER_ADMIN_USERNAME);
        registry.add("admin.super-admin.password", () -> SUPER_ADMIN_PASSWORD);
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
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected DoctorProfileRepository doctorProfileRepository;

    @Autowired
    protected SuperAdminJwtService superAdminJwtService;

    @AfterEach
    void cleanDatabase() {
        doctorProfileRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        accountRepository.deleteAll();
        clinicRepository.deleteAll();
    }

    protected static String basicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 040-super-admin-rbac-login T014a: issues a {@code SUPER_ADMIN}-audience bearer JWT
     * directly (bypassing login), the same shape every {@code /api/v1/admin/**} caller
     * must now present. Every existing subclass calling this method is unaffected by the
     * Basic Auth -> JWT migration - only this one method's body changed.
     */
    protected String superAdminAuthHeader() {
        return "Bearer " + superAdminJwtService.issueToken(SUPER_ADMIN_USERNAME);
    }

    protected Clinic saveClinic(String name, boolean verified) {
        Clinic clinic = new Clinic(name, "1 Test Street", null, null);
        clinic.setVerified(verified);
        return clinicRepository.save(clinic);
    }

    /** Creates a real, valid staff Account (001) - used to prove even valid staff credentials are rejected here (FR-004). */
    protected Account saveStaffAccount(String email, String plaintextPassword) {
        Account account = new Account("Staff Person", email, passwordEncoder.encode(plaintextPassword), "ST-0001", null);
        return accountRepository.save(account);
    }

    /** 007: a Doctor Account + Doctor Profile pair, not yet linked to any clinic. */
    protected DoctorProfile saveDoctorProfile(
            String email, String licenseNumber, String specialization, boolean licenseVerified) {
        Account account = accountRepository.save(
                new Account("Dr. Test", email, passwordEncoder.encode("Str0ng!Pass"), "DR-" + licenseNumber, null));
        DoctorProfile profile = new DoctorProfile(account, specialization, licenseNumber, 5);
        profile.setLicenseVerified(licenseVerified);
        return doctorProfileRepository.save(profile);
    }

    /** 007 FR-008: links a Doctor Profile's Account to a clinic via an (optionally deactivated) Role Assignment. */
    protected RoleAssignment linkDoctorToClinic(DoctorProfile profile, Clinic clinic, boolean active) {
        RoleAssignment roleAssignment = new RoleAssignment(profile.getAccount(), clinic, RoleAssignment.Role.Doctor);
        if (!active) {
            roleAssignment.deactivate(com.cms.identity.account.RoleAssignment.DeactivationReason.RESIGNED);
        }
        return roleAssignmentRepository.save(roleAssignment);
    }
}
