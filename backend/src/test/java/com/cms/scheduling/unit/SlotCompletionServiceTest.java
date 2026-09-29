package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.exception.ForbiddenException;
import com.cms.scheduling.exception.SlotNotCompletableException;
import com.cms.scheduling.exception.SlotNotFoundException;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.SessionDelayService;
import com.cms.scheduling.service.SlotCompletionService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 057-day-sheet-status-overhaul: unit slice (no Spring context, no DB) for {@link
 * SlotCompletionService}'s role/status eligibility matrix - no unit test existed for this
 * service before this feature, only the Testcontainers-backed SlotCompletion*Test integration
 * suite (which this complements, not replaces).
 */
@ExtendWith(MockitoExtension.class)
class SlotCompletionServiceTest {

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private SessionDelayService sessionDelayService;

    @Mock
    private Slot slot;

    @Mock
    private Session session;

    @Mock
    private Clinic clinic;

    @Mock
    private DoctorProfile doctorProfile;

    @Mock
    private com.cms.identity.account.domain.Account doctorAccount;

    private final UUID clinicId = UUID.randomUUID();
    private final UUID slotId = UUID.randomUUID();
    private final UUID callerAccountId = UUID.randomUUID();

    private SlotCompletionService service() {
        return new SlotCompletionService(slotRepository, roleAssignmentRepository, sessionDelayService);
    }

    // Only findById/getSession/getClinic/getId and the doctor-profile chain are consumed on
    // every path (authorization is always evaluated in full before any early return); mode,
    // status, and the time-check fields are only reached on paths that get that far - lenient
    // so a test whose caller is rejected earlier doesn't fail on their being "unused".
    private void stubSlotFound(SlotStatus status) {
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(slot));
        when(slot.getSession()).thenReturn(session);
        when(session.getClinic()).thenReturn(clinic);
        when(clinic.getId()).thenReturn(clinicId);
        when(session.getDoctorProfile()).thenReturn(doctorProfile);
        when(doctorProfile.getAccount()).thenReturn(doctorAccount);
        when(doctorAccount.getId()).thenReturn(UUID.randomUUID()); // a different account than the caller, by default
        lenient().when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        lenient().when(slot.getStatus()).thenReturn(status);
        lenient().when(session.getSessionDate()).thenReturn(LocalDate.now());
        lenient().when(slot.getStartTime()).thenReturn(LocalTime.now().minusHours(1));
    }

    private void stubStaffRole(boolean isClinicAdmin, boolean isOperations) {
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin))
                .thenReturn(isClinicAdmin);
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerAccountId, clinicId, RoleAssignment.Role.Operations))
                .thenReturn(isOperations);
    }

    @Test
    void clinicAdminCompletesFromBooked_existingPathUnchanged() {
        stubSlotFound(SlotStatus.BOOKED);
        stubStaffRole(true, false);

        Slot result = service().completeSlot(callerAccountId, clinicId, slotId);

        assertThat(result).isSameAs(slot);
    }

    @Test
    void operationsCompletesFromAppeared_newlyAdditive() {
        stubSlotFound(SlotStatus.APPEARED);
        stubStaffRole(false, true);

        service().completeSlot(callerAccountId, clinicId, slotId);

        org.mockito.Mockito.verify(slot).setStatus(SlotStatus.COMPLETED);
    }

    @Test
    void treatingDoctorCompletesFromAppeared() {
        stubSlotFound(SlotStatus.APPEARED);
        stubStaffRole(false, false);
        when(doctorAccount.getId()).thenReturn(callerAccountId); // this caller IS the treating doctor

        service().completeSlot(callerAccountId, clinicId, slotId);

        org.mockito.Mockito.verify(slot).setStatus(SlotStatus.COMPLETED);
    }

    // ---- 064-queue-send-in-complete (FR-003): queue tokens complete through the same action ----

    private void stubQueueToken(SlotStatus status) {
        stubSlotFound(status);
        lenient().when(session.getMode()).thenReturn(ScheduleMode.QUEUE);
        lenient().when(slot.isUntimed()).thenReturn(true);
        lenient().when(slot.getStartTime()).thenReturn(null);
    }

    @Test
    void staffCompleteAWaitingQueueToken() {
        stubQueueToken(SlotStatus.BOOKED);
        stubStaffRole(false, true);

        service().completeSlot(callerAccountId, clinicId, slotId);

        org.mockito.Mockito.verify(slot).setStatus(SlotStatus.COMPLETED);
    }

    @Test
    void theTreatingDoctorCompletesAQueueTokenThatIsInWithThem() {
        stubQueueToken(SlotStatus.APPEARED);
        stubStaffRole(false, false);
        when(doctorAccount.getId()).thenReturn(callerAccountId);

        service().completeSlot(callerAccountId, clinicId, slotId);

        org.mockito.Mockito.verify(slot).setStatus(SlotStatus.COMPLETED);
    }

    @Test
    void treatingDoctorIsRejectedFromBooked() {
        stubSlotFound(SlotStatus.BOOKED);
        stubStaffRole(false, false);
        when(doctorAccount.getId()).thenReturn(callerAccountId);

        assertThatThrownBy(() -> service().completeSlot(callerAccountId, clinicId, slotId))
                .isInstanceOf(SlotNotCompletableException.class);
    }

    @Test
    void nonTreatingDoctorWithNoStaffRoleIsForbidden() {
        stubSlotFound(SlotStatus.APPEARED);
        stubStaffRole(false, false);
        // doctorAccount.getId() (stubbed in stubSlotFound) is a different, random UUID than callerAccountId

        assertThatThrownBy(() -> service().completeSlot(callerAccountId, clinicId, slotId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void slotNotFoundAtClinicIsRejected() {
        when(slotRepository.findById(slotId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().completeSlot(callerAccountId, clinicId, slotId))
                .isInstanceOf(SlotNotFoundException.class);
    }
}
