package com.cms.identity.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.RoleAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

/**
 * T012: a fault injected at the last step of the atomic create (RoleAssignment save)
 * leaves zero Clinic/Account/RoleAssignment rows behind - proves FR-002/FR-003's
 * atomicity, not just the specific email/staff-code uniqueness races (those are T013,
 * T014). RoleAssignmentRepository is mocked to throw; Clinic and Account are still
 * written to the real Postgres container via the real repositories first, so this
 * genuinely exercises the @Transactional rollback, not just a unit-level mock.
 */
class RegisterClinicRollbackTest extends AbstractIntegrationTest {

    @MockBean
    private RoleAssignmentRepository roleAssignmentRepositoryOverride;

    @Test
    void partialFailureRollsBackEveryWrite() throws Exception {
        when(roleAssignmentRepositoryOverride.save(any()))
                .thenThrow(new DataIntegrityViolationException("simulated failure for T012"));

        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("rollback-test@sunrise-clinic.example")))
                .andExpect(status().is5xxServerError());

        assertThat(clinicRepository.count()).isZero();
        assertThat(accountRepository.count()).isZero();
        // roleAssignmentRepositoryOverride is mocked, so its own count is meaningless here;
        // the point is that the Clinic/Account rows the service wrote before the failure
        // are gone too, proving the whole transaction rolled back together.
    }
}
