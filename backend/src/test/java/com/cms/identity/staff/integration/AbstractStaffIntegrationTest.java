package com.cms.identity.staff.integration;

import com.cms.identity.account.Account;
import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.StaffJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
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
public abstract class AbstractStaffIntegrationTest {

    /** 005-last-active-clinicadmin-protection (T014): same credentials pattern as AbstractAdminIntegrationTest. */
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

    protected static String superAdminAuthHeader() {
        String credentials = SUPER_ADMIN_USERNAME + ":" + SUPER_ADMIN_PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
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

    @Autowired
    protected StaffJwtService staffJwtService;

    @AfterEach
    void cleanDatabase() {
        doctorProfileRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        accountRepository.deleteAll();
        clinicRepository.deleteAll();
    }

    protected Clinic saveClinic(String name) {
        return clinicRepository.save(new Clinic(name, "1 Test Street", null, null));
    }

    protected Account saveAccount(String email, String plaintextPassword, String staffCode) {
        Account account = new Account("Test User", email, passwordEncoder.encode(plaintextPassword), staffCode, null);
        return accountRepository.save(account);
    }

    /** Creates a Clinic + an active ClinicAdmin Account/RoleAssignment for it, returning a valid staff JWT for that Account. */
    protected String clinicAdminToken(Clinic clinic) {
        Account admin = saveAccount("admin-" + clinic.getId() + "@example.com", "Str0ng!Pass", "CA-" + clinic.getId());
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        return staffJwtService.issueToken(admin.getId());
    }

    /** A non-ClinicAdmin (Operations) staff token, for authorization-rejection tests. */
    protected String nonClinicAdminToken(Clinic clinic) {
        Account operations =
                saveAccount("ops-" + clinic.getId() + "@example.com", "Str0ng!Pass", "OP-" + clinic.getId());
        roleAssignmentRepository.save(new RoleAssignment(operations, clinic, RoleAssignment.Role.Operations));
        return staffJwtService.issueToken(operations.getId());
    }

    protected static String validOperationsRequestJson(String email) {
        return """
                { "name": "Jamie Ops", "email": "%s", "role": "Operations" }
                """
                .formatted(email);
    }

    protected static String validDoctorRequestJson(String email) {
        return doctorRequestJson(email, "Cardiology", "KA-12345");
    }

    /** 007: lets dedup tests control specialization/licenseNumber independently of the fixed defaults above. */
    protected static String doctorRequestJson(String email, String specialization, String licenseNumber) {
        return """
                {
                  "name": "Dr. Priya Nair",
                  "email": "%s",
                  "role": "Doctor",
                  "doctor": { "specialization": "%s", "licenseNumber": "%s", "experienceYears": 7 }
                }
                """
                .formatted(email, specialization, licenseNumber);
    }
}
