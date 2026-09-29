package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.scheduling.api.TodaySessionStatsController;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.dto.TodaySessionStatsResponse;
import com.cms.scheduling.exception.NotStaffedAtClinicException;
import com.cms.scheduling.repository.SlotRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * 051-staff-dashboard-enhancement T005: pure Mockito, no Spring context - mirrors
 * ClinicSessionListController's own "any active role at this clinic" gate (research.md
 * Decision 2), reused verbatim rather than a new authorization mechanism (FR-009).
 *
 * <p>doctor-console-cross-doctor-leak fix: also covers ClinicSessionListController's doctor
 * self-scoping, now reused here so a Doctor-only caller's "Completed today" count reflects only
 * their own slots, not the whole clinic's.
 */
@ExtendWith(MockitoExtension.class)
class TodaySessionStatsControllerTest {

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    @Mock
    private RoleAssignment roleAssignment;

    @Mock
    private DoctorProfile doctorProfile;

    private TodaySessionStatsController newController() {
        return new TodaySessionStatsController(slotRepository, roleAssignmentRepository, doctorProfileRepository);
    }

    private static UsernamePasswordAuthenticationToken authenticationFor(UUID accountId) {
        return new UsernamePasswordAuthenticationToken(accountId, null);
    }

    @Test
    void foldsGroupedCountsIntoCompletedAndNoShowCounts() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(roleAssignment));
        when(roleAssignment.getRole()).thenReturn(RoleAssignment.Role.ClinicAdmin);
        when(slotRepository.countStatusByClinicAndDate(eq(clinicId), any(), isNull()))
                .thenReturn(List.of(
                        statusCount(SlotStatus.COMPLETED, 3),
                        statusCount(SlotStatus.NO_SHOW, 1),
                        statusCount(SlotStatus.BOOKED, 5)));

        TodaySessionStatsResponse response = newController().get(clinicId, authenticationFor(callerAccountId));

        assertThat(response.completedCount()).isEqualTo(3);
        assertThat(response.noShowCount()).isEqualTo(1);
    }

    @Test
    void missingStatusGroupsDefaultToZero() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(roleAssignment));
        when(roleAssignment.getRole()).thenReturn(RoleAssignment.Role.Operations);
        when(slotRepository.countStatusByClinicAndDate(eq(clinicId), any(), isNull()))
                .thenReturn(List.of());

        TodaySessionStatsResponse response = newController().get(clinicId, authenticationFor(callerAccountId));

        assertThat(response.completedCount()).isZero();
        assertThat(response.noShowCount()).isZero();
    }

    @Test
    void callerWithNoActiveRoleAtClinicIsRejected() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> newController().get(clinicId, authenticationFor(callerAccountId)))
                .isInstanceOf(NotStaffedAtClinicException.class);
    }

    @Test
    void doctorOnlyCallerSeesOnlyTheirOwnCounts() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID doctorProfileId = UUID.randomUUID();
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(roleAssignment));
        when(roleAssignment.getRole()).thenReturn(RoleAssignment.Role.Doctor);
        when(doctorProfileRepository.findByAccount_Id(callerAccountId)).thenReturn(Optional.of(doctorProfile));
        when(doctorProfile.getId()).thenReturn(doctorProfileId);
        when(slotRepository.countStatusByClinicAndDate(eq(clinicId), any(), eq(doctorProfileId)))
                .thenReturn(List.of(statusCount(SlotStatus.COMPLETED, 1)));

        TodaySessionStatsResponse response = newController().get(clinicId, authenticationFor(callerAccountId));

        assertThat(response.completedCount()).isEqualTo(1);
    }

    @Test
    void callerWithBothDoctorAndClinicAdminRolesSeesTheWholeClinic() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        RoleAssignment clinicAdminRole = org.mockito.Mockito.mock(RoleAssignment.class);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(roleAssignment, clinicAdminRole));
        when(roleAssignment.getRole()).thenReturn(RoleAssignment.Role.Doctor);
        when(clinicAdminRole.getRole()).thenReturn(RoleAssignment.Role.ClinicAdmin);
        when(slotRepository.countStatusByClinicAndDate(eq(clinicId), any(), isNull()))
                .thenReturn(List.of(statusCount(SlotStatus.COMPLETED, 4)));

        TodaySessionStatsResponse response = newController().get(clinicId, authenticationFor(callerAccountId));

        assertThat(response.completedCount()).isEqualTo(4);
    }

    @Test
    void doctorOnlyCallerWithNoResolvableDoctorProfileFailsClosedToZero() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(roleAssignment));
        when(roleAssignment.getRole()).thenReturn(RoleAssignment.Role.Doctor);
        when(doctorProfileRepository.findByAccount_Id(callerAccountId)).thenReturn(Optional.empty());

        TodaySessionStatsResponse response = newController().get(clinicId, authenticationFor(callerAccountId));

        assertThat(response.completedCount()).isZero();
        assertThat(response.noShowCount()).isZero();
    }

    private static SlotRepository.SlotStatusCount statusCount(SlotStatus status, long count) {
        return new SlotRepository.SlotStatusCount() {
            @Override
            public SlotStatus getStatus() {
                return status;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}
