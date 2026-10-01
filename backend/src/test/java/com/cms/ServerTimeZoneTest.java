package com.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/**
 * PB-005 (owner decision 2026-10-01: the product runs in IST): every time rule uses the JVM
 * default zone (LocalDateTime.now(), Clock.systemDefaultZone(), and SQL CURRENT_DATE via the
 * JDBC session zone), so the application pins that zone itself instead of trusting the host.
 */
class ServerTimeZoneTest {

    @Test
    void applicationZoneIsIst() {
        assertThat(CmsApplication.ZONE).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }

    @Test
    void pinTimeZoneSetsTheJvmDefaultToIst() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            CmsApplication.pinTimeZone();
            assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Kolkata"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /** The test JVM itself runs in IST (build.gradle), so tests exercise the same clock as production. */
    @Test
    void testJvmRunsInIst() {
        assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Kolkata"));
    }
}
