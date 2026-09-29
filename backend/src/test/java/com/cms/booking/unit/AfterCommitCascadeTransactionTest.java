package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.service.ClinicRejectionCascadeService;
import com.cms.booking.service.DeVerificationCascadeService;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * real-bug-fix 2026-09-24 (008 de-verification cascade, verified live): every cascade entry point
 * invoked from an AFTER_COMMIT {@code @TransactionalEventListener} must open its own transaction.
 * With the default REQUIRED propagation it joins the already-committed transaction, and every
 * write it makes - queue-mode cancellations, patient notifications, staff inbox notices - is
 * silently discarded. Reproduced on the dev stack: un-verifying a clinic cancelled its fixed-time
 * booking (that one path has its own REQUIRES_NEW) but published no patient notification.
 *
 * <p>A structural guard, because the Testcontainers integration tier that would catch the lost
 * writes end-to-end cannot run in every environment.
 */
class AfterCommitCascadeTransactionTest {

    @ParameterizedTest(name = "{0}.{1}")
    @CsvSource({
        "com.cms.booking.service.DeVerificationCascadeService, cascadeFromClinic",
        "com.cms.booking.service.DeVerificationCascadeService, cascadeFromDoctor",
        "com.cms.booking.service.ClinicRejectionCascadeService, cascadeFromClinic",
    })
    void cascadeEntryPointsCalledAfterCommitRunInTheirOwnTransaction(String className, String methodName)
            throws Exception {
        Method method = Class.forName(className).getMethod(methodName, UUID.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).as("%s.%s must be @Transactional", className, methodName).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @SuppressWarnings("unused") // keeps the two classes referenced so a rename breaks compilation, not just this test
    private static final Class<?>[] GUARDED = {DeVerificationCascadeService.class, ClinicRejectionCascadeService.class};
}
