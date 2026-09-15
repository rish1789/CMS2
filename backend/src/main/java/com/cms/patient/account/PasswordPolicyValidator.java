package com.cms.patient.account;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Password policy (BDD §4 / FR-002): minimum 8 characters, at least one lowercase
 * letter, one uppercase letter, one digit, and one special character.
 *
 * <p>Intentionally a local copy rather than a dependency on
 * {@code com.cms.identity.account.PasswordPolicyValidator} (001) - FR-008 requires "no
 * shared... authentication logic" between the two identity systems, and this is
 * authentication-flow logic (unlike {@code IndianMobileNumberValidator}, which tasks.md
 * explicitly chose to consolidate into a neutral shared package as a general-purpose
 * format validator, not an auth rule). Small, stable, and cheap to keep in sync if the
 * policy ever changes - not worth coupling the two modules over.
 *
 * <p>Explicit bean name below: Spring's default (the unqualified simple class name) collides
 * with {@code com.cms.identity.account.PasswordPolicyValidator}'s own default bean name,
 * which a full application context boot rejects as a {@code ConflictingBeanDefinitionException}
 * - never caught by this session's own tests, since every {@code @SpringBootTest} in this
 * codebase is Testcontainers-gated and fails before Spring ever attempts to start the context.
 * Found only by actually booting the full app (038's own quickstart run). Both classes are
 * injected by type everywhere, so renaming only the bean name (not the class) is safe.
 */
@Component("patientAccountPasswordPolicyValidator")
public class PasswordPolicyValidator {

    public static final String RULE_MIN_LENGTH = "minLength";
    public static final String RULE_LOWERCASE = "lowercase";
    public static final String RULE_UPPERCASE = "uppercase";
    public static final String RULE_DIGIT = "digit";
    public static final String RULE_SPECIAL_CHARACTER = "specialCharacter";

    /** @return the list of failed rule codes; empty if the password satisfies every rule. */
    public List<String> validate(String password) {
        List<String> failedRules = new ArrayList<>();
        if (password == null || password.length() < 8) {
            failedRules.add(RULE_MIN_LENGTH);
        }
        if (password == null || password.chars().noneMatch(Character::isLowerCase)) {
            failedRules.add(RULE_LOWERCASE);
        }
        if (password == null || password.chars().noneMatch(Character::isUpperCase)) {
            failedRules.add(RULE_UPPERCASE);
        }
        if (password == null || password.chars().noneMatch(Character::isDigit)) {
            failedRules.add(RULE_DIGIT);
        }
        if (password == null || password.chars().allMatch(Character::isLetterOrDigit)) {
            failedRules.add(RULE_SPECIAL_CHARACTER);
        }
        return failedRules;
    }
}
