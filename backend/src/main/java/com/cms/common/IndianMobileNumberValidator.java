package com.cms.common;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Indian numbering plan (BDD §4): 10 digits starting 6-9, with an optional {@code +91}
 * or {@code 0} prefix. The field itself is optional wherever it's used; this validator
 * only judges non-null, non-blank values.
 *
 * <p>Relocated here from {@code com.cms.identity.common} by 002-patient-account-login
 * (T026): both {@code com.cms.identity} (001) and {@code com.cms.patient} (002) need
 * this same validation, and neither module should depend on the other for it - this
 * neutral shared package is the correct home, not a reach-through into either module.
 */
@Component
public class IndianMobileNumberValidator {

    private static final Pattern PATTERN = Pattern.compile("^(?:\\+91|0)?[6-9]\\d{9}$");

    public boolean isValid(String mobile) {
        if (mobile == null || mobile.isBlank()) {
            return true; // optional field - absence is always valid
        }
        return PATTERN.matcher(mobile.trim()).matches();
    }
}
