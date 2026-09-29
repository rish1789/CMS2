package com.cms.identity.staff.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.AccountRepository;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.account.service.PasswordPolicyValidator;
import com.cms.identity.account.service.TemporaryPasswordGenerator;
import com.cms.identity.staff.dto.ResetStaffPasswordResponse;
import com.cms.identity.staff.exception.ForbiddenException;
import com.cms.identity.staff.exception.InvalidPasswordException;
import com.cms.identity.staff.exception.RoleAssignmentNotFoundException;
import com.cms.identity.staff.service.StaffPasswordResetService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * real-bug-fix 2026-09-17: pure Mockito, no Spring context - mirrors StaffDeactivationService's
 * own "only an active ClinicAdmin for THIS specific clinic" gate and target-lookup shape.
 */
@ExtendWith(MockitoExtension.class)
class StaffPasswordResetServiceTest {

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TemporaryPasswordGenerator temporaryPasswordGenerator;

    @Mock
    private PasswordPolicyValidator passwordPolicyValidator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleAssignment targetRoleAssignment;

    @Mock
    private Account targetAccount;

    private final UUID callerAccountId = UUID.randomUUID();
    private final UUID clinicId = UUID.randomUUID();
    private final UUID targetAccountId = UUID.randomUUID();

    private StaffPasswordResetService newService() {
        return new StaffPasswordResetService(
                roleAssignmentRepository, accountRepository, temporaryPasswordGenerator, passwordPolicyValidator, passwordEncoder);
    }

    private void grantClinicAdmin() {
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin))
                .thenReturn(true);
    }

    @BeforeEach
    void commonStubs() {
        lenient().when(targetRoleAssignment.getAccount()).thenReturn(targetAccount);
    }

    @Test
    void nonClinicAdminCallerIsForbidden() {
        assertThatThrownBy(() -> newService().resetPassword(callerAccountId, clinicId, targetAccountId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void unknownTargetAtThisClinicIsNotFound() {
        grantClinicAdmin();
        when(roleAssignmentRepository.findByAccount_IdAndClinic_Id(targetAccountId, clinicId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().resetPassword(callerAccountId, clinicId, targetAccountId))
                .isInstanceOf(RoleAssignmentNotFoundException.class);
    }

    @Test
    void aClinicAdminTargetIsForbiddenWithNoOverride() {
        grantClinicAdmin();
        when(targetRoleAssignment.getRole()).thenReturn(RoleAssignment.Role.ClinicAdmin);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_Id(targetAccountId, clinicId))
                .thenReturn(Optional.of(targetRoleAssignment));

        assertThatThrownBy(() -> newService().resetPassword(callerAccountId, clinicId, targetAccountId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void generatesAndHashesANewTemporaryPasswordForADoctorTarget() {
        grantClinicAdmin();
        when(targetRoleAssignment.getRole()).thenReturn(RoleAssignment.Role.Doctor);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_Id(targetAccountId, clinicId))
                .thenReturn(Optional.of(targetRoleAssignment));
        when(targetAccount.getId()).thenReturn(targetAccountId);
        when(targetAccount.getEmail()).thenReturn("doctor@example.com");
        when(targetAccount.getStaffCode()).thenReturn("DR-4467");
        when(temporaryPasswordGenerator.generate()).thenReturn("Gener4ted!Pass");
        when(passwordEncoder.encode("Gener4ted!Pass")).thenReturn("hashed-value");

        ResetStaffPasswordResponse response = newService().resetPassword(callerAccountId, clinicId, targetAccountId);

        verify(targetAccount).setPasswordHash("hashed-value");
        verify(accountRepository).save(targetAccount);
        assertThat(response.accountId()).isEqualTo(targetAccountId);
        assertThat(response.email()).isEqualTo("doctor@example.com");
        assertThat(response.staffCode()).isEqualTo("DR-4467");
        assertThat(response.temporaryPassword()).isEqualTo("Gener4ted!Pass");
    }

    @Test
    void setPasswordRejectsAPolicyViolatingChoiceBeforeTouchingTheTarget() {
        // Mirrors ClinicVerificationService.setClinicAdminPassword's own ordering - password
        // policy is checked before authorization, so no ClinicAdmin grant is needed here.
        when(passwordPolicyValidator.validate("weak")).thenReturn(List.of("Must be at least 8 characters"));

        assertThatThrownBy(() -> newService().setPassword(callerAccountId, clinicId, targetAccountId, "weak"))
                .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    void setPasswordHashesAndStoresTheCallerChosenPasswordForAnOperationsTarget() {
        grantClinicAdmin();
        when(passwordPolicyValidator.validate("Str0ng!ChosenPass")).thenReturn(List.of());
        when(targetRoleAssignment.getRole()).thenReturn(RoleAssignment.Role.Operations);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_Id(targetAccountId, clinicId))
                .thenReturn(Optional.of(targetRoleAssignment));
        when(targetAccount.getId()).thenReturn(targetAccountId);
        when(targetAccount.getEmail()).thenReturn("ops@example.com");
        when(targetAccount.getStaffCode()).thenReturn("OP-1001");
        when(passwordEncoder.encode("Str0ng!ChosenPass")).thenReturn("hashed-chosen-value");

        ResetStaffPasswordResponse response =
                newService().setPassword(callerAccountId, clinicId, targetAccountId, "Str0ng!ChosenPass");

        verify(targetAccount).setPasswordHash("hashed-chosen-value");
        verify(accountRepository).save(targetAccount);
        assertThat(response.temporaryPassword()).isEqualTo("Str0ng!ChosenPass");
    }
}
