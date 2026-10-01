package com.cms.common.login.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.integration.AbstractSessionCancellationIntegrationTest;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.domain.PatientAccount;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 075-login-hardening (D-3C-1, D-3C-2) through the real filter chain and Postgres: one generic
 * login error, a per-identifier lockout that treats unregistered identifiers exactly like real
 * ones, and staff sessions cut off once the account has no active clinic role. Every request
 * comes from its own client address so the separate per-IP limiter (047) never interferes.
 */
class LoginHardeningTest extends AbstractSessionCancellationIntegrationTest {

    private static final AtomicInteger CLIENT = new AtomicInteger();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearAttempts() {
        jdbcTemplate.update("DELETE FROM login_attempt");
    }

    private static String nextClient() {
        int n = CLIENT.incrementAndGet();
        return "10.75." + (n / 250) + "." + (n % 250 + 1);
    }

    private ResultActions staffLogin(String identifier, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/staff/login")
                .with(r -> {
                    r.setRemoteAddr(nextClient());
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"%s\",\"password\":\"%s\"}".formatted(identifier, password)));
    }

    private ResultActions patientLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/patients/login")
                .with(r -> {
                    r.setRemoteAddr(nextClient());
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    // --- US1: no enumeration ------------------------------------------------------------------

    @Test
    void staffLoginAnswersAnUnknownAccountAndAWrongPasswordIdentically() throws Exception {
        DoctorProfile doctor = saveDoctorStaffedAt(saveClinic());
        String email = doctor.getAccount().getEmail();

        String unknown = staffLogin("nobody-" + email, "Wrong1!pass").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String wrong = staffLogin(email, "Wrong1!pass").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrong).isEqualTo(unknown);
        staffLogin(email, "Str0ng!Pass").andExpect(status().isOk());
    }

    @Test
    void patientLoginAnswersAnUnknownAccountAndAWrongPasswordIdentically() throws Exception {
        PatientAccount account = savePatientAccount();

        String unknown = patientLogin("nobody-" + account.getEmail(), "Wrong1!pass").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String wrong = patientLogin(account.getEmail(), "Wrong1!pass").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(wrong).isEqualTo(unknown);
        patientLogin(account.getEmail(), "Str0ng!Pass").andExpect(status().isOk());
    }

    // --- US2: lockout ---------------------------------------------------------------------------

    @Test
    void fiveWrongPasswordsLockARealStaffAccountEvenAgainstTheRightPassword() throws Exception {
        String email = saveDoctorStaffedAt(saveClinic()).getAccount().getEmail();
        for (int i = 0; i < 5; i++) {
            staffLogin(email, "Wrong1!pass").andExpect(status().isUnauthorized());
        }

        staffLogin(email, "Str0ng!Pass")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("TOO_MANY_LOGIN_ATTEMPTS"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void anUnregisteredIdentifierIsLockedExactlyLikeARealOne() throws Exception {
        String realEmail = savePatientAccount().getEmail();
        String ghostEmail = "ghost-" + realEmail;
        for (int i = 0; i < 5; i++) {
            patientLogin(realEmail, "Wrong1!pass").andExpect(status().isUnauthorized());
            patientLogin(ghostEmail, "Wrong1!pass").andExpect(status().isUnauthorized());
        }

        String real = patientLogin(realEmail, "Wrong1!pass").andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();
        String ghost = patientLogin(ghostEmail, "Wrong1!pass").andExpect(status().isTooManyRequests())
                .andReturn().getResponse().getContentAsString();
        assertThat(ghost).isEqualTo(real);
    }

    @Test
    void aSuccessfulLoginClearsTheCountAndRealmsAreSeparate() throws Exception {
        PatientAccount patient = savePatientAccount();
        for (int i = 0; i < 4; i++) {
            patientLogin(patient.getEmail(), "Wrong1!pass").andExpect(status().isUnauthorized());
        }
        patientLogin(patient.getEmail(), "Str0ng!Pass").andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            patientLogin(patient.getEmail(), "Wrong1!pass").andExpect(status().isUnauthorized());
        }
        // Four new failures after a success: still not locked.
        patientLogin(patient.getEmail(), "Str0ng!Pass").andExpect(status().isOk());

        // The same address failing on staff login does not touch the patient count.
        for (int i = 0; i < 5; i++) {
            staffLogin(patient.getEmail(), "Wrong1!pass").andExpect(status().isUnauthorized());
        }
        patientLogin(patient.getEmail(), "Str0ng!Pass").andExpect(status().isOk());
    }

    @Test
    void identifiersDifferingOnlyInCaseAndSpacesShareOneCount() throws Exception {
        String email = savePatientAccount().getEmail();
        for (int i = 0; i < 5; i++) {
            patientLogin(i % 2 == 0 ? email.toUpperCase() : " " + email + " ", "Wrong1!pass");
        }
        patientLogin(email, "Str0ng!Pass").andExpect(status().isTooManyRequests());
    }

    @Test
    void concurrentFailuresAreAllCounted() throws Exception {
        String email = savePatientAccount().getEmail();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MvcResult>> results = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return patientLogin(email, "Wrong1!pass").andReturn();
                }));
            }
            start.countDown();
            for (Future<MvcResult> f : results) {
                assertThat(f.get(60, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(401);
            }
        } finally {
            pool.shutdownNow();
        }
        patientLogin(email, "Str0ng!Pass").andExpect(status().isTooManyRequests());
    }

    // --- US3: deactivated staff ------------------------------------------------------------------

    private String tokenOf(String email) throws Exception {
        String body = staffLogin(email, "Str0ng!Pass").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private ResultActions myClinics(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/mine").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void deactivationAtTheLastClinicEndsTheSessionAndBlocksLogin() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        String email = doctor.getAccount().getEmail();
        String token = tokenOf(email);
        myClinics(token).andExpect(status().isOk());

        deactivateEveryRoleOf(doctor);

        myClinics(token).andExpect(status().isUnauthorized());
        staffLogin(email, "Str0ng!Pass")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NO_ACTIVE_CLINIC_ACCESS"));
        // A wrong password still gets the generic answer - the no-access message needs the right one.
        staffLogin(email, "Wrong1!pass").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void deactivationAtOneOfTwoClinicsKeepsTheSession() throws Exception {
        Clinic first = saveClinic();
        Clinic second = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(first);
        linkDoctorToClinic(doctor, second, true);
        String token = tokenOf(doctor.getAccount().getEmail());

        RoleAssignment atFirst = roleAssignmentRepository.findByAccount_IdAndClinic_Id(doctor.getAccount().getId(), first.getId())
                .orElseThrow();
        atFirst.deactivate(RoleAssignment.DeactivationReason.RESIGNED);
        roleAssignmentRepository.save(atFirst);

        myClinics(token).andExpect(status().isOk()).andExpect(jsonPath("$.clinics.length()").value(1));
        staffLogin(doctor.getAccount().getEmail(), "Str0ng!Pass").andExpect(status().isOk());
    }

    private void deactivateEveryRoleOf(DoctorProfile doctor) {
        for (RoleAssignment role : roleAssignmentRepository.findByAccount_IdAndActiveTrue(doctor.getAccount().getId())) {
            role.deactivate(RoleAssignment.DeactivationReason.RESIGNED);
            roleAssignmentRepository.save(role);
        }
    }
}
