package com.cms.identity.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T013: TemporaryPasswordGenerator output always satisfies PasswordPolicyValidator, across many samples (FR-006, SC-003). */
class TemporaryPasswordGeneratorTest {

    private final PasswordPolicyValidator passwordPolicyValidator = new PasswordPolicyValidator();
    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator(passwordPolicyValidator);

    @Test
    void everyGeneratedPasswordSatisfiesThePolicy() {
        for (int i = 0; i < 500; i++) {
            String password = generator.generate();
            assertThat(passwordPolicyValidator.validate(password))
                    .as("generated password should satisfy every policy rule: " + password)
                    .isEmpty();
        }
    }

    @Test
    void generatedPasswordsAreNotAllIdentical() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            generated.add(generator.generate());
        }
        assertThat(generated).hasSizeGreaterThan(40);
    }
}
