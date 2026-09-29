package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.booking.service.ScheduleDeletionService;
import com.cms.booking.service.SessionDeletionService;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.scheduling.domain.Schedule;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.exception.ForbiddenException;
import com.cms.scheduling.exception.ScheduleNotFoundException;
import com.cms.scheduling.repository.ScheduleRepository;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.repository.SlotRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 055-schedule-break-window: pure-Mockito coverage of the orchestration this service owns -
 * auth, scoping the Schedule to its clinic/doctor, and (the one rule that matters most) never
 * letting a real-activity Session block the whole deletion, only detaching it instead - done
 * via {@link SessionDeletionService#hasRealActivity} plus this class's own repositories, all in
 * one transaction (this class's own Javadoc explains why calling the transactional {@code
 * deleteSession} and catching its exception does not work).
 */
@ExtendWith(MockitoExtension.class)
class ScheduleDeletionServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ClinicRepository clinicRepository;

    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private SessionDeletionService sessionDeletionService;

    @Mock
    private Clinic clinic;

    @Mock
    private DoctorProfile doctorProfile;

    @Mock
    private Account doctorAccount;

    @Mock
    private Schedule schedule;

    private final UUID callerAccountId = UUID.randomUUID();
    private final UUID clinicId = UUID.randomUUID();
    private final UUID doctorProfileId = UUID.randomUUID();
    private final UUID doctorAccountId = UUID.randomUUID();
    private final UUID scheduleId = UUID.randomUUID();

    @BeforeEach
    void commonStubs() {
        lenient().when(clinicRepository.findById(clinicId)).thenReturn(Optional.of(clinic));
        lenient().when(doctorProfileRepository.findById(doctorProfileId)).thenReturn(Optional.of(doctorProfile));
        lenient().when(doctorProfile.getAccount()).thenReturn(doctorAccount);
        lenient().when(doctorAccount.getId()).thenReturn(doctorAccountId);
        lenient().when(schedule.getId()).thenReturn(scheduleId);
        lenient().when(schedule.getClinic()).thenReturn(clinic);
        lenient().when(clinic.getId()).thenReturn(clinicId);
        lenient().when(schedule.getDoctorProfile()).thenReturn(doctorProfile);
        lenient().when(doctorProfile.getId()).thenReturn(doctorProfileId);
        lenient().when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
    }

    private ScheduleDeletionService newService() {
        return new ScheduleDeletionService(
                scheduleRepository,
                clinicRepository,
                doctorProfileRepository,
                roleAssignmentRepository,
                sessionRepository,
                slotRepository,
                sessionDeletionService);
    }

    private void grantClinicAdmin() {
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin))
                .thenReturn(true);
    }

    @Test
    void callerWithNeitherClinicAdminNorDoctorSelfRoleIsForbidden() {
        assertThatThrownBy(() -> newService().deleteSchedule(callerAccountId, clinicId, doctorProfileId, scheduleId))
                .isInstanceOf(ForbiddenException.class);
        verify(scheduleRepository, never()).delete(any());
    }

    @Test
    void unknownScheduleIsRejected() {
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().deleteSchedule(callerAccountId, clinicId, doctorProfileId, scheduleId))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void aScheduleWhoseSessionIdBelongsToAnotherClinicIsRejected() {
        UUID otherClinicId = UUID.randomUUID();
        when(clinic.getId()).thenReturn(otherClinicId);

        assertThatThrownBy(() -> newService().deleteSchedule(callerAccountId, clinicId, doctorProfileId, scheduleId))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void deletesEveryZeroActivitySessionThenTheScheduleItself() {
        grantClinicAdmin();
        Session sessionA = org.mockito.Mockito.mock(Session.class);
        Session sessionB = org.mockito.Mockito.mock(Session.class);
        when(sessionA.getId()).thenReturn(UUID.randomUUID());
        when(sessionB.getId()).thenReturn(UUID.randomUUID());
        when(sessionRepository.findBySchedule_Id(scheduleId)).thenReturn(List.of(sessionA, sessionB));
        when(sessionDeletionService.hasRealActivity(any())).thenReturn(false);
        Slot slotA = org.mockito.Mockito.mock(Slot.class);
        when(slotRepository.findBySession_Id(sessionA.getId())).thenReturn(List.of(slotA));
        when(slotRepository.findBySession_Id(sessionB.getId())).thenReturn(List.of());

        newService().deleteSchedule(callerAccountId, clinicId, doctorProfileId, scheduleId);

        verify(sessionRepository).delete(sessionA);
        verify(sessionRepository).delete(sessionB);
        verify(sessionA, never()).detachSchedule();
        verify(sessionB, never()).detachSchedule();
        verify(scheduleRepository).delete(schedule);
    }

    @Test
    void aSessionWithRealActivityIsDetachedInsteadOfBlockingTheWholeDeletion() {
        grantClinicAdmin();
        Session blockedSession = org.mockito.Mockito.mock(Session.class);
        Session freeSession = org.mockito.Mockito.mock(Session.class);
        when(freeSession.getId()).thenReturn(UUID.randomUUID());
        when(sessionRepository.findBySchedule_Id(scheduleId)).thenReturn(List.of(blockedSession, freeSession));
        when(sessionDeletionService.hasRealActivity(blockedSession)).thenReturn(true);
        when(sessionDeletionService.hasRealActivity(freeSession)).thenReturn(false);
        when(slotRepository.findBySession_Id(freeSession.getId())).thenReturn(List.of());

        newService().deleteSchedule(callerAccountId, clinicId, doctorProfileId, scheduleId);

        verify(blockedSession).detachSchedule();
        verify(sessionRepository, never()).delete(blockedSession);
        verify(sessionRepository).delete(freeSession);
        verify(freeSession, never()).detachSchedule();
        // The Schedule itself is still fully removed - detaching, not blocking, is the whole point.
        verify(scheduleRepository, times(1)).delete(schedule);
    }
}
