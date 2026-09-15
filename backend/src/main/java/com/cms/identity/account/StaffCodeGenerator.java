package com.cms.identity.account;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates a short, memorable staff code (e.g. {@code CA-4821}), globally unique across
 * the platform (FR-013). Shared mechanism: 004-staff-onboarding-direct-hire generates
 * codes for later-hired Doctor/Operations staff from this same namespace (different
 * prefix per role), so no collision can occur between a founding ClinicAdmin's code and
 * a later hire's code.
 *
 * <p>Uniqueness is only fully guaranteed by the database's unique constraint on {@code
 * account.staff_code} (Constitution Principle IV) - the retry loop here is a best-effort,
 * fast-path check against the same race the DB constraint closes for real.
 */
@Component
public class StaffCodeGenerator {

    private static final int MAX_ATTEMPTS = 20;
    private static final int CODE_MIN = 1000;
    private static final int CODE_MAX = 9999;

    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    public StaffCodeGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * @param rolePrefix e.g. "CA" for ClinicAdmin, "DR" for Doctor, "OP" for Operations
     */
    public String generate(String rolePrefix) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = rolePrefix + "-" + (CODE_MIN + random.nextInt(CODE_MAX - CODE_MIN + 1));
            if (!accountRepository.existsByStaffCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Unable to generate a unique staff code after " + MAX_ATTEMPTS + " attempts for prefix " + rolePrefix);
    }
}
