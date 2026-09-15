package com.cms.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Indian numbering plan validation. Relocated from {@code com.cms.identity.common} by
 * 002-patient-account-login (T026) alongside the class under test.
 */
class IndianMobileNumberValidatorTest {

    private final IndianMobileNumberValidator validator = new IndianMobileNumberValidator();

    @ParameterizedTest
    @NullAndEmptySource
    void absentMobileIsValidBecauseFieldIsOptional(String mobile) {
        assertThat(validator.isValid(mobile)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"9876543210", "+919876543210", "09876543210", "6123456789"})
    void validNumbersAccepted(String mobile) {
        assertThat(validator.isValid(mobile)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "12345", // too short, wrong starting digit
                "5876543210", // starts with 5, not 6-9
                "98765432100", // too long
                "+1-9876543210", // wrong country code format
                "abcdefghij" // not digits
            })
    void invalidNumbersRejected(String mobile) {
        assertThat(validator.isValid(mobile)).isFalse();
    }
}
