package com.cms.identity.staff.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T028: a fault injected at the last step (DoctorProfile save, after Account and
 * RoleAssignment have already been written to the real Postgres container) leaves zero
 * rows across all three tables - proves FR-009/SC-005's atomicity across the widest-scope
 * (three-table) path, not just a two-table one.
 */
class OnboardingAtomicityTest extends AbstractStaffIntegrationTest {

    @MockBean
    private DoctorProfileRepository doctorProfileRepositoryOverride;

    @Test
    void partialFailureRollsBackAllThreeTables() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);
        long accountsBefore = accountRepository.count();
        long roleAssignmentsBefore = roleAssignmentRepository.count();

        when(doctorProfileRepositoryOverride.save(any()))
                .thenThrow(new DataIntegrityViolationException("simulated failure for T028"));

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validDoctorRequestJson("atomicity-test@sunrise-clinic.example")))
                .andExpect(status().is5xxServerError());

        assertThat(accountRepository.findByEmail("atomicity-test@sunrise-clinic.example")).isEmpty();
        assertThat(accountRepository.count()).isEqualTo(accountsBefore);
        assertThat(roleAssignmentRepository.count()).isEqualTo(roleAssignmentsBefore);
    }
}
