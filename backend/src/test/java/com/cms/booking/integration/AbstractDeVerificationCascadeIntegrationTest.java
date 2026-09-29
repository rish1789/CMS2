package com.cms.booking.integration;

import com.cms.identity.admin.config.SuperAdminJwtService;
import com.cms.identity.admin.service.ClinicVerificationService;
import com.cms.identity.admin.service.DoctorVerificationService;
import com.cms.inbox.repository.InboxItemRepository;
import com.cms.waitlist.domain.WaitlistEntry;
import com.cms.waitlist.repository.WaitlistEntryRepository;
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

    @Autowired
    private InboxItemRepository inboxItemRepository;

    /** Runs before the superclass's own {@code cleanDatabase} (JUnit 5's subclass-before-superclass @AfterEach order) - waitlist_entry references patient_account, and the cascade's inbox items reference clinic (no ON DELETE CASCADE), so both must go first. */
    @AfterEach
    void cleanWaitlistEntries() {
        inboxItemRepository.deleteAll();
        waitlistEntryRepository.deleteAll();
    }

    @Autowired
    private SuperAdminJwtService superAdminJwtService;

    /** 040-super-admin-rbac-login: the Super Admin realm accepts only its own JWT (Basic auth was retired). */
    protected String superAdminAuthHeader() {
        return "Bearer " + superAdminJwtService.issueToken(SUPER_ADMIN_USERNAME);
    }

    protected WaitlistEntry saveWaitingEntry(
            com.cms.identity.clinic.Clinic clinic,
            com.cms.identity.doctor.DoctorProfile doctor,
            com.cms.patient.account.domain.PatientAccount patientAccount) {
        return waitlistEntryRepository.save(new WaitlistEntry(clinic, patientAccount, doctor, null));
    }
}
