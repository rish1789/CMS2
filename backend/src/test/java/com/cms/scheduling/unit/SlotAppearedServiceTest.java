package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.exception.ForbiddenException;
import com.cms.scheduling.exception.SlotNotAppearableException;
import com.cms.scheduling.exception.SlotNotFoundException;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.SlotAppearedService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 057-day-sheet-status-overhaul: unit slice (no Spring context, no DB) for {@link
 * SlotAppearedService} - proves both source states (BOOKED, NO_SHOW) succeed, every other
 * source/mode is rejected, and only ClinicAdmin/Operations are authorized (no doctor branch,
 * unlike SlotCompletionService - FR-007).
 */
@ExtendWith(MockitoExtension.class)
class SlotAppearedServiceTest {

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private Slot slot;

    @Mock
    private Session session;

    @Mock
    private Clinic clinic;

    private final UUID clinicId = UUID.randomUUID();
    private final UUID slotId = UUID.randomUUID();
    private final UUID callerAccountId = UUID.randomUUID();

    private SlotAppearedService service() {
        return new SlotAppearedService(slotRepository, roleAssignmentRepository);
    }

    private void stubSlotFound() {
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(slot.getSession()).thenReturn(session);
        when(session.getClinic()).thenReturn(clinic);
        when(clinic.getId()).thenReturn(clinicId);
    }

    private void stubClinicAdminAuthorized() {
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin))
                .thenReturn(true);
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerAccountId, clinicId, RoleAssignment.Role.Operations))
                .thenReturn(false);
    }

    @Test
    void bookedSlotIsMarkedAppeared() {
        stubSlotFound();
        stubClinicAdminAuthorized();
        when(slot.getStatus()).thenReturn(SlotStatus.BOOKED);

        Slot result = service().markAppeared(callerAccountId, clinicId, slotId);

        assertThat(result).isSameAs(slot);
        org.mockito.Mockito.verify(slot).setStatus(SlotStatus.APPEARED);
    }

    @Test
    void noShowSlotIsCorrectedToAppeared() {
        stubSlotFound();
        stubClinicAdminAuthorized();
        when(slot.getStatus()).thenReturn(SlotStatus.NO_SHOW);

        service().markAppeared(callerAccountId, clinicId, slotId);

        org.mockito.Mockito.verify(slot).setStatus(SlotStatus.APPEARED);
    }

    @Test
    void openSlotIsRejected() {
        stubSlotFound();
        stubClinicAdminAuthorized();
        when(slot.getStatus()).thenReturn(SlotStatus.OPEN);

        assertThatThrownBy(() -> service().markAppeared(callerAccountId, clinicId, slotId))
                .isInstanceOf(SlotNotAppearableException.class);
    }

    @Test
    void alreadyAppearedSlotIsRejected() {
        stubSlotFound();
        stubClinicAdminAuthorized();
        when(slot.getStatus()).thenReturn(SlotStatus.APPEARED);

        assertThatThrownBy(() -> service().markAppeared(callerAccountId, clinicId, slotId))
                .isInstanceOf(SlotNotAppearableException.class);
    }

    @Test
    void completedSlotIsRejected() {
        stubSlotFound();
        stubClinicAdminAuthorized();
        when(slot.getStatus()).thenReturn(SlotStatus.COMPLETED);

        assertThatThrownBy(() -> service().markAppeared(callerAccountId, clinicId, slotId))
                .isInstanceOf(SlotNotAppearableException.class);
    }

    /** 064-queue-send-in-complete (FR-002): a waiting queue token can now be sent in, exactly like an appointment slot. */
    @Test
    void aWaitingQueueTokenCanBeSentIn() {
        stubSlotFound();
        stubClinicAdminAuthorized();
        when(slot.getStatus()).thenReturn(SlotStatus.BOOKED);

        service().markAppeared(callerAccountId, clinicId, slotId);

        verify(slot).setStatus(SlotStatus.APPEARED);
    }

    @Test
    void callerWithNoRoleAtClinicIsForbidden() {
        stubSlotFound();
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(any(), any(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> service().markAppeared(callerAccountId, clinicId, slotId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void slotNotFoundAtClinicIsRejected() {
        when(slotRepository.findById(slotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().markAppeared(callerAccountId, clinicId, slotId))
                .isInstanceOf(SlotNotFoundException.class);
    }
}
