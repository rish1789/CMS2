package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

/**
 * Guards src/test/resources/application.properties: if the integration-test context ever runs the
 * real scheduler again, the per-minute sweeps go back to changing fixtures mid-test.
 */
class ScheduledSweepsDisabledInTestsTest extends AbstractNoShowDetectionIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void theIntegrationTestContextRunsNoScheduledJobs() {
        assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
    }
}
