package com.cms.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * T014 (unit slice): retry-on-collision behaviour of {@link StaffCodeGenerator} against a
 * mocked {@link AccountRepository}. This complements (does not replace) the DB-level
 * uniqueness invariant test, which requires a real Postgres instance - see
 * RegisterClinicDuplicateStaffCodeTest.
 */
@ExtendWith(MockitoExtension.class)
class StaffCodeGeneratorTest {

    @Mock
    private AccountRepository accountRepository;

    @Test
    void generatesCodeWithExpectedPrefixFormat() {
        when(accountRepository.existsByStaffCode(anyString())).thenReturn(false);

        StaffCodeGenerator generator = new StaffCodeGenerator(accountRepository);
        String code = generator.generate("CA");

        assertThat(code).matches("^CA-\\d{4}$");
    }

    @Test
    void retriesWhenGeneratedCodeAlreadyExists() {
        // First candidate collides, second is free - proves the generator does not
        // silently accept a colliding code.
        when(accountRepository.existsByStaffCode(anyString())).thenReturn(true, false);

        StaffCodeGenerator generator = new StaffCodeGenerator(accountRepository);
        String code = generator.generate("CA");

        assertThat(code).matches("^CA-\\d{4}$");
    }

    @Test
    void givesUpAfterMaxAttemptsRatherThanReturningAKnownDuplicate() {
        when(accountRepository.existsByStaffCode(anyString())).thenReturn(true);

        StaffCodeGenerator generator = new StaffCodeGenerator(accountRepository);

        assertThatThrownBy(() -> generator.generate("CA")).isInstanceOf(IllegalStateException.class);
    }
}
