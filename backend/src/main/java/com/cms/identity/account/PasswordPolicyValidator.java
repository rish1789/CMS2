package com.cms.identity.account;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Password policy (BDD §4 / FR-009): minimum 8 characters, at least one lowercase
 * letter, one uppercase letter, one digit, and one special character.
 */
@Component
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
