package com.cms.patient.record.integration;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.patient.account.PatientAccount;
import com.cms.patient.account.PatientAccountRepository;
import com.cms.patient.record.Patient;
import com.cms.patient.record.PatientRepository;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 009: shared fixtures for the service-only patient-linking feature - no HTTP layer, no MockMvc. */
@SpringBootTest
@Testcontainers
public abstract class AbstractPatientRecordIntegrationTest {

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
    protected ClinicRepository clinicRepository;

    @Autowired
    protected PatientAccountRepository patientAccountRepository;

    @Autowired
    protected PatientRepository patientRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanDatabase() {
        patientRepository.deleteAll();
        patientAccountRepository.deleteAll();
        clinicRepository.deleteAll();
    }

    protected Clinic saveClinic(String name) {
        return clinicRepository.save(new Clinic(name, "1 Test Street", null, null));
    }

    protected PatientAccount savePatientAccount(String email, String mobile) {
        return patientAccountRepository.save(
                new PatientAccount(email, passwordEncoder.encode("Str0ng!Pass"), mobile));
    }

    /** An unlinked (walk-in) Patient record - patientAccount is null. */
    protected Patient saveWalkInPatient(Clinic clinic, String name, String phone) {
        return patientRepository.save(new Patient(clinic, null, name, phone));
    }
}
