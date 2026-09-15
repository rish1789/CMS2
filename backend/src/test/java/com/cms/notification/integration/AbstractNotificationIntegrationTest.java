package com.cms.notification.integration;

import com.cms.notification.NotificationEventRepository;
import com.cms.notification.NotificationEventService;
import com.cms.notification.NotificationSender;
import com.cms.patient.account.PatientAccount;
import com.cms.patient.account.PatientAccountRepository;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 011/037: shared fixtures for the service-only notification pipeline feature - no HTTP
 * layer, no MockMvc. Registers a recording {@link NotificationSender} (037) so tests can
 * assert exactly what would have been sent, without a real provider or log-scraping.
 */
@SpringBootTest
@Testcontainers
public abstract class AbstractNotificationIntegrationTest {

    /** 037: records every {@code send} call in place of the real {@code LoggingNotificationSender} bean. */
    public record RecordedSend(String channel, String recipient, String message) {}

    @TestConfiguration
    static class RecordingNotificationSenderConfig {

        @Bean
        @Primary
        RecordingNotificationSender recordingNotificationSender() {
            return new RecordingNotificationSender();
        }
    }

    static class RecordingNotificationSender implements NotificationSender {
        final List<RecordedSend> sends = new CopyOnWriteArrayList<>();

        @Override
        public void send(String channel, String recipient, String message) {
            sends.add(new RecordedSend(channel, recipient, message));
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("cms_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected NotificationEventService notificationEventService;

    @Autowired
    protected NotificationEventRepository notificationEventRepository;

    @Autowired
    protected PatientAccountRepository patientAccountRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected RecordingNotificationSender recordingNotificationSender;

    private int accountCounter = 0;

    @AfterEach
    void cleanDatabase() {
        notificationEventRepository.deleteAll();
        patientAccountRepository.deleteAll();
        recordingNotificationSender.sends.clear();
    }

    protected List<RecordedSend> recordedSends() {
        return recordingNotificationSender.sends;
    }

    protected PatientAccount savePatientAccount(boolean pushOptIn, boolean smsOptIn, String mobile) {
        accountCounter++;
        PatientAccount account = new PatientAccount(
                "patient" + accountCounter + "@example.com", passwordEncoder.encode("Str0ng!Pass"), mobile);
        account.setPushOptIn(pushOptIn);
        account.setSmsOptIn(smsOptIn);
        return patientAccountRepository.save(account);
    }
}
