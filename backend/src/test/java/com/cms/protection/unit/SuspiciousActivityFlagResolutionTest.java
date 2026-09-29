package com.cms.protection.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.cms.identity.clinic.Clinic;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.protection.domain.SuspiciousActivityFlag;
import com.cms.protection.domain.SuspiciousActivityFlagStatus;
import com.cms.protection.domain.SuspiciousActivitySignalType;
import com.cms.protection.exception.FlagAlreadyResolvedException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 060-booking-abuse-prevention (spec.md FR-023): the flag's own one-way OUTSTANDING -> RESOLVED transition. */
class SuspiciousActivityFlagResolutionTest {

    private SuspiciousActivityFlag newFlag() {
        return new SuspiciousActivityFlag(
                mock(PatientAccount.class),
                mock(Clinic.class),
                SuspiciousActivitySignalType.REPEATED_CANCELLATIONS,
                "4 cancellations in the last 30 days",
                Instant.now());
    }

    @Test
    void resolvingAnOutstandingFlagSetsStatusAndTheResolvedFieldsTogether() {
        SuspiciousActivityFlag flag = newFlag();
        Instant resolvedAt = Instant.now();

        flag.resolve(resolvedAt, "admin@example.com");

        assertThat(flag.getStatus()).isEqualTo(SuspiciousActivityFlagStatus.RESOLVED);
        assertThat(flag.getResolvedAt()).isEqualTo(resolvedAt);
        assertThat(flag.getResolvedBy()).isEqualTo("admin@example.com");
    }

    @Test
    void anAlreadyResolvedFlagCannotBeResolvedAgain() {
        SuspiciousActivityFlag flag = newFlag();
        flag.resolve(Instant.now(), "admin@example.com");

        assertThatThrownBy(() -> flag.resolve(Instant.now(), "someone-else@example.com"))
                .isInstanceOf(FlagAlreadyResolvedException.class);
    }

    @Test
    void aNewFlagStartsOutstanding() {
        assertThat(newFlag().getStatus()).isEqualTo(SuspiciousActivityFlagStatus.OUTSTANDING);
    }
}
