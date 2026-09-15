package com.cms.booking.integration;

import com.cms.identity.admin.ClinicVerificationService;
import com.cms.identity.admin.DoctorVerificationService;
import com.cms.waitlist.WaitlistEntry;
import com.cms.waitlist.WaitlistEntryRepository;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 033: extends 029's Fixed-Time/Queue-mode + Patient Account fixture (this codebase's
 * established {@code com.cms.booking} test-fixture inheritance pattern) with the Super Admin
 * Basic Auth support {@code AbstractAdminIntegrationTest} (003) already established, plus
 * {@link ClinicVerificationService}/{@link DoctorVerificationService} (the two trigger actions)
 * and {@link WaitlistEntryRepository} (for FR-004's real-waitlist-bump assertion).
 */
public abstract class AbstractDeVerificationCascadeIntegrationTest extends AbstractSessionCancellationIntegrationTest {

    protected static final String SUPER_ADMIN_USERNAME = "test-super-admin";
    protected static final String SUPER_ADMIN_PASSWORD = "Str0ng!Pass";

    @DynamicPropertySource
    static void superAdminProperties(DynamicPropertyRegistry registry) {
        registry.add("admin.super-admin.username", () -> SUPER_ADMIN_USERNAME);
        registry.add("admin.super-admin.password", () -> SUPER_ADMIN_PASSWORD);
    }

    @Autowired
    protected ClinicVerificationService clinicVerificationService;

    @Autowired
    protected DoctorVerificationService doctorVerificationService;

    @Autowired
    protected WaitlistEntryRepository waitlistEntryRepository;

    /** Runs before the superclass's own {@code cleanDatabase} (JUnit 5's subclass-before-superclass @AfterEach order) - waitlist_entry references patient_account, so it must go first. */
    @AfterEach
    void cleanWaitlistEntries() {
        waitlistEntryRepository.deleteAll();
    }

    protected static String superAdminAuthHeader() {
        String credentials = SUPER_ADMIN_USERNAME + ":" + SUPER_ADMIN_PASSWORD;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    protected WaitlistEntry saveWaitingEntry(
            com.cms.identity.clinic.Clinic clinic,
            com.cms.identity.doctor.DoctorProfile doctor,
            com.cms.patient.account.PatientAccount patientAccount) {
        return waitlistEntryRepository.save(new WaitlistEntry(clinic, patientAccount, doctor, null));
    }
}
