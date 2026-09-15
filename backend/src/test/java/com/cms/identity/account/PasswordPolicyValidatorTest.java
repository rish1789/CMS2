package com.cms.identity.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** T015 (unit slice): every password-policy rule (FR-009) is enforced independently. */
class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Test
    void validPasswordHasNoFailedRules() {
        assertThat(validator.validate("Str0ng!Pass")).isEmpty();
    }

    @Test
    void tooShortFailsMinLength() {
        assertThat(validator.validate("Ab1!")).contains(PasswordPolicyValidator.RULE_MIN_LENGTH);
    }

    @Test
    void missingLowercaseFails() {
        assertThat(validator.validate("STR0NG!PASS")).contains(PasswordPolicyValidator.RULE_LOWERCASE);
    }

    @Test
    void missingUppercaseFails() {
        assertThat(validator.validate("str0ng!pass")).contains(PasswordPolicyValidator.RULE_UPPERCASE);
    }

    @Test
    void missingDigitFails() {
        assertThat(validator.validate("Strong!Pass")).contains(PasswordPolicyValidator.RULE_DIGIT);
    }

    @Test
    void missingSpecialCharacterFails() {
        assertThat(validator.validate("Str0ngPass1")).contains(PasswordPolicyValidator.RULE_SPECIAL_CHARACTER);
    }

    @Test
    void allRulesFailedAreReportedTogetherNotJustTheFirst() {
        List<String> failed = validator.validate("short");
        assertThat(failed)
                .containsExactlyInAnyOrder(
                        PasswordPolicyValidator.RULE_MIN_LENGTH,
                        PasswordPolicyValidator.RULE_UPPERCASE,
                        PasswordPolicyValidator.RULE_DIGIT,
                        PasswordPolicyValidator.RULE_SPECIAL_CHARACTER);
    }
}
