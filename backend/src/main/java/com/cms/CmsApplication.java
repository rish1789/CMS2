package com.cms;

import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Relocated here from {@code com.cms.identity.IdentityApplication} by
 * 002-patient-account-login: {@code @SpringBootApplication}'s implicit component scan
 * only covers its own package and below, so with the application class inside
 * {@code com.cms.identity} the sibling {@code com.cms.patient} and {@code com.cms.common}
 * packages (both new in this feature) were never scanned - a real bug, not just a test
 * config issue, since it meant none of this feature's beans were ever registered at
 * runtime either. Living at {@code com.cms} (the common parent of every module) fixes
 * this for good, not just for the packages that happen to exist today.
 */
@SpringBootApplication
public class CmsApplication {

    /**
     * PB-005 (owner decision 2026-10-01): the product serves Indian clinics, so every time rule
     * runs in IST. Those rules read the JVM default zone (LocalDateTime.now(),
     * Clock.systemDefaultZone()), and the PostgreSQL driver sets each connection's session zone -
     * which SQL CURRENT_DATE uses - from that same default. Pinning it here, before Spring
     * starts, makes all of them IST whatever zone the host runs in (a UTC server otherwise
     * shifts no-show marking, the 2 h cancellation cutoff and "not yet started" by 5 h 30 min).
     */
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    public static void main(String[] args) {
        pinTimeZone();
        SpringApplication.run(CmsApplication.class, args);
    }

    static void pinTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }
}
