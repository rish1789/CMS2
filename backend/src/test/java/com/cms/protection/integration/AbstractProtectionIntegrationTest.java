package com.cms.protection.integration;

import com.cms.identity.account.config.StaffJwtService;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.AccountRepository;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.account.repository.PatientAccountRepository;
import com.cms.protection.domain.SuspiciousActivityFlag;
import com.cms.protection.domain.SuspiciousActivitySignalType;
import com.cms.protection.repository.SuspiciousActivityFlagRepository;
import java.time.Instant;
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

/**
 * 060-booking-abuse-prevention: a lightweight fixture for the `protection` module's own
 * integration tier (this module's first) - local fixture helpers rather than reusing `booking`'s
 * abstract base classes, matching this session's established "local fixture duplication over
 * shared base class modification" convention (059's own precedent). Flags are seeded directly via
 * the repository, not through a full FlagDetectionService sweep - this tier tests the API's own
 * clinic-scoping boundary, not signal-detection logic (already unit-tested).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractProtectionIntegrationTest {

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
    protected PatientAccountRepository patientAccountRepository;

    @Autowired
    protected SuspiciousActivityFlagRepository flagRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected StaffJwtService staffJwtService;

    private int counter = 0;

    @AfterEach
    void cleanDatabase() {
        flagRepository.deleteAll();
        patientAccountRepository.deleteAll();
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

    protected String clinicAdminToken(Clinic clinic) {
        counter++;
        Account admin = accountRepository.save(new Account(
                "Admin " + counter, "admin" + counter + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "CA-" + counter, null));
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        return staffJwtService.issueToken(admin.getId());
    }

    protected PatientAccount savePatientAccount() {
        counter++;
        return patientAccountRepository.save(new PatientAccount(
                "patient" + counter + "@example.com", passwordEncoder.encode("Str0ng!Pass"), null));
    }

    protected SuspiciousActivityFlag saveOutstandingFlag(PatientAccount patientAccount, Clinic clinic, String reason) {
        return flagRepository.save(new SuspiciousActivityFlag(
                patientAccount, clinic, SuspiciousActivitySignalType.REPEATED_CANCELLATIONS, reason, Instant.now()));
    }
}
