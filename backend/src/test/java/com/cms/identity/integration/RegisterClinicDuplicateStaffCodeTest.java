package com.cms.identity.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.identity.account.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * T014: proves the DB-level unique constraint on account.staff_code (uq_account_staff_code,
 * created by the T007 migration) genuinely rejects a duplicate - the invariant this test
 * exists to cover per the analyze-stage finding (D1). Complements
 * StaffCodeGeneratorTest's mocked-repository retry-logic test: that test proves the
 * *generator's* collision handling; this one proves the *database* itself won't allow a
 * duplicate to persist even if application-level generation logic were ever buggy.
 */
class RegisterClinicDuplicateStaffCodeTest extends AbstractIntegrationTest {

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Test
    void databaseRejectsADuplicateStaffCodeEvenBypassingTheGenerator() {
        String sharedStaffCode = "CA-1234";

        Account first = new Account(
                "Dr. Asha Rao",
                "first@sunrise-clinic.example",
                passwordEncoder.encode("Str0ng!Pass"),
                sharedStaffCode,
                null);
        accountRepository.saveAndFlush(first);

        Account second = new Account(
                "Dr. Vikram Shah",
                "second@sunrise-clinic.example",
                passwordEncoder.encode("Str0ng!Pass"),
                sharedStaffCode,
                null);

        assertThatThrownBy(() -> accountRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(accountRepository.count()).isEqualTo(1);
    }
}
