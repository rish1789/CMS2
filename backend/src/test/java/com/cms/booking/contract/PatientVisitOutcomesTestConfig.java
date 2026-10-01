package com.cms.booking.contract;

import com.cms.booking.service.PatientVisitOutcomes;
import java.time.LocalDateTime;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 069-patient-visit-outcomes: the real patient outcome/eligibility policy for {@code @WebMvcTest}
 * slices, on the system clock - so contract tests of the patient booking endpoints keep exercising
 * the actual cancellation checks rather than a mock of them.
 */
@TestConfiguration
public class PatientVisitOutcomesTestConfig {

    @Bean
    PatientVisitOutcomes patientVisitOutcomes() {
        return new PatientVisitOutcomes(LocalDateTime::now);
    }
}
