package com.cms.clinical.integration;

import com.cms.clinical.PrescriptionRepository;
import com.cms.clinical.PrescriptionService;
import com.cms.identity.account.Account;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 035: extends 034's fixture (mirrors this codebase's established fixture-inheritance pattern),
 * adding the one role 030 never needed - Operations. Cleans up {@code prescription_item} via raw
 * SQL rather than a repository - no production repository for it exists at all (no delete code
 * path, not even generic inherited infrastructure, research.md R4).
 */
public abstract class AbstractPrescriptionIntegrationTest extends AbstractConsultationNoteIntegrationTest {

    @Autowired
    protected PrescriptionRepository prescriptionRepository;

    @Autowired
    protected PrescriptionService prescriptionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int operationsCounter = 0;

    /** Runs before the superclass's own cleanup (JUnit 5's subclass-before-superclass @AfterEach order) - prescription/prescription_item reference booking, which the superclass deletes. */
    @AfterEach
    void cleanPrescriptions() {
        jdbcTemplate.update("DELETE FROM prescription_item");
        prescriptionRepository.deleteAll();
    }

    protected String operationsToken(Clinic clinic) {
        operationsCounter++;
        Account operations = accountRepository.save(new Account(
                "Operations " + operationsCounter,
                "ops" + operationsCounter + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"),
                "OP-" + operationsCounter,
                null));
        roleAssignmentRepository.save(new RoleAssignment(operations, clinic, RoleAssignment.Role.Operations));
        return staffJwtService.issueToken(operations.getId());
    }
}
