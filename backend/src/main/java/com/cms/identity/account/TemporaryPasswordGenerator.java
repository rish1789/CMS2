package com.cms.identity.account;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates a random one-time temporary password guaranteed, by construction, to satisfy
 * {@link PasswordPolicyValidator} (FR-006, SC-003) - neither that validator (which only
 * checks a submitted password) nor {@code PasswordEncoder} (which only hashes one)
 * produces a password string, so this is a genuinely new piece, not a duplicate.
 */
@Component
public class TemporaryPasswordGenerator {

    private static final String LOWERCASE = "abcdefghijkmnpqrstuvwxyz"; // no l/o, avoids visual ambiguity
    private static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // no I/O
    private static final String DIGITS = "23456789"; // no 0/1
    private static final String SPECIAL = "!@#$%^&*";
    private static final String ALL = LOWERCASE + UPPERCASE + DIGITS + SPECIAL;
    private static final int LENGTH = 12;

    private final SecureRandom random = new SecureRandom();
    private final PasswordPolicyValidator passwordPolicyValidator;

    public TemporaryPasswordGenerator(PasswordPolicyValidator passwordPolicyValidator) {
        this.passwordPolicyValidator = passwordPolicyValidator;
    }

    /** @return a random password satisfying every rule in {@link PasswordPolicyValidator}. */
    public String generate() {
        String candidate;
        do {
            candidate = generateCandidate();
            // Guarantees the policy holds even if it's ever changed independently of this
            // class - re-validated, not just assumed correct by construction alone.
        } while (!passwordPolicyValidator.validate(candidate).isEmpty());
        return candidate;
    }

    private String generateCandidate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        // Guarantee at least one of each required character class up front.
        sb.append(LOWERCASE.charAt(random.nextInt(LOWERCASE.length())));
        sb.append(UPPERCASE.charAt(random.nextInt(UPPERCASE.length())));
        sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        sb.append(SPECIAL.charAt(random.nextInt(SPECIAL.length())));
        for (int i = sb.length(); i < LENGTH; i++) {
            sb.append(ALL.charAt(random.nextInt(ALL.length())));
        }
        // Shuffle so the guaranteed classes aren't always in the same leading positions.
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
