package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.scheduling.config.SessionGenerationSchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/**
 * The per-minute sweeps (no-show, auto-completion, waitlist expiry) fired inside integration tests
 * and changed fixture data mid-test - intermittent failures in SessionDelayNoOutstandingDelayTest
 * and SessionCancellationSuccessTest. {@code cms.scheduling.enabled=false} (test resources) turns
 * the scheduler off; anywhere the property is absent it stays on, so production is unchanged.
 */
class SchedulingToggleTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(SessionGenerationSchedulingConfig.class);

    @Test
    void schedulingIsOnWhenThePropertyIsAbsent() {
        runner.run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void schedulingIsOnWhenExplicitlyEnabled() {
        runner.withPropertyValues("cms.scheduling.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void schedulingIsOffWhenDisabled() {
        runner.withPropertyValues("cms.scheduling.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
