package com.cms.patient.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.patient.account.PatientAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T010: the same email is independently usable for a Patient Account signup and a
 * staff Account (001-style clinic) registration - no cross-system uniqueness check
 * exists (FR-004). Deliberately touches both modules' repositories - this is a test
 * verifying the boundary, not production code reaching across it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PatientAccountCrossSystemEmailTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("cms_test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PatientAccountRepository patientAccountRepository;

    @Autowired
    private ClinicRepository clinicRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @AfterEach
    void cleanDatabase() {
        roleAssignmentRepository.deleteAll();
        accountRepository.deleteAll();
        clinicRepository.deleteAll();
        patientAccountRepository.deleteAll();
    }

    @Test
    void sameEmailUsableIndependentlyInBothIdentitySystems() throws Exception {
        String sharedEmail = "shared@example.com";

        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "%s", "password": "Str0ng!Pass" }
                                """
                                        .formatted(sharedEmail)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "clinic": { "name": "Shared Email Clinic", "address": "1 Test Road" },
                                  "admin": { "name": "Dr. Test", "email": "%s", "password": "Str0ng!Pass" }
                                }
                                """
                                        .formatted(sharedEmail)))
                .andExpect(status().isCreated());

        assertThat(patientAccountRepository.existsByEmail(sharedEmail)).isTrue();
        assertThat(accountRepository.existsByEmail(sharedEmail)).isTrue();
        assertThat(patientAccountRepository.count()).isEqualTo(1);
        assertThat(accountRepository.count()).isEqualTo(1);
    }
}
