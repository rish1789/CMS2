package com.cms.identity.account.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.account.service.RejectedClinicAccessGate;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 062-rejected-clinic-gating (FR-007, research.md Decision 6, tasks.md T023): the per-request
 * decision behind the staff access interceptor. Only a rejected clinic is ever gated, and at a
 * rejected clinic only an active ClinicAdmin role gets through. A caller with no role there at
 * all is allowed through this gate - the existing per-service "not staffed" checks keep
 * answering that case exactly as before.
 */
@ExtendWith(MockitoExtension.class)
class RejectedClinicAccessGateTest {

    @Mock ClinicRepository clinicRepository;
    @Mock RoleAssignmentRepository roleAssignmentRepository;

    private final UUID accountId = UUID.randomUUID();
    private final UUID clinicId = UUID.randomUUID();

    private RejectedClinicAccessGate gate() {
        return new RejectedClinicAccessGate(clinicRepository, roleAssignmentRepository);
    }

    private Clinic clinic(boolean rejected) {
        Clinic clinic = new Clinic("Clinic", "1 Main St", null, null);
        if (rejected) {
            clinic.reject(Clinic.RejectionReason.DUPLICATE_REGISTRATION, null, "super-admin");
        }
        when(clinicRepository.findById(clinicId)).thenReturn(Optional.of(clinic));
        return clinic;
    }

    private RoleAssignment role(Clinic clinic, RoleAssignment.Role role) {
        return new RoleAssignment(mock(Account.class), clinic, role);
    }

    @Test
    void aClinicThatIsNotRejectedIsAllowedWithoutAnyRoleLookup() {
        clinic(false);

        assertThat(gate().allows(accountId, clinicId)).isTrue();
        verifyNoInteractions(roleAssignmentRepository);
    }

    @Test
    void theClinicAdminOfARejectedClinicIsAllowed() {
        Clinic clinic = clinic(true);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(accountId, clinicId))
                .thenReturn(List.of(role(clinic, RoleAssignment.Role.ClinicAdmin)));

        assertThat(gate().allows(accountId, clinicId)).isTrue();
    }

    @Test
    void aDoctorAtARejectedClinicIsDenied() {
        Clinic clinic = clinic(true);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(accountId, clinicId))
                .thenReturn(List.of(role(clinic, RoleAssignment.Role.Doctor)));

        assertThat(gate().allows(accountId, clinicId)).isFalse();
    }

    @Test
    void aCallerWithNoRoleAtARejectedClinicIsLeftToTheExistingNotStaffedChecks() {
        clinic(true);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(accountId, clinicId))
                .thenReturn(List.of());

        assertThat(gate().allows(accountId, clinicId)).isTrue();
    }

    @Test
    void anUnknownClinicIsLeftToTheExistingNotFoundChecks() {
        when(clinicRepository.findById(clinicId)).thenReturn(Optional.empty());

        assertThat(gate().allows(accountId, clinicId)).isTrue();
    }
}
