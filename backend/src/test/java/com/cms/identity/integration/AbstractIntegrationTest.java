package com.cms.identity.integration;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.cms.identity.account.AccountRepository;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.clinic.ClinicRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

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
    protected MockMvc mockMvc;

    @Autowired
    protected ClinicRepository clinicRepository;

    @Autowired
    protected AccountRepository accountRepository;

    @Autowired
    protected RoleAssignmentRepository roleAssignmentRepository;

    @AfterEach
    void cleanDatabase() {
        roleAssignmentRepository.deleteAll();
        accountRepository.deleteAll();
        clinicRepository.deleteAll();
    }

    protected static String validRequestJson(String email) {
        return """
                {
                  "clinic": {
                    "name": "Sunrise Clinic",
                    "address": "12 MG Road, Bengaluru"
                  },
                  "admin": {
                    "name": "Dr. Asha Rao",
                    "email": "%s",
                    "password": "Str0ng!Pass"
                  }
                }
                """
                .formatted(email);
    }
}
