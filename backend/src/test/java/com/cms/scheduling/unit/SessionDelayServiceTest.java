package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.exception.NotStaffedAtClinicException;
import com.cms.scheduling.exception.SessionNotFoundException;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.SessionDelayService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * doctor-console-cross-doctor-leak fix: this service's first unit tier for {@code
 * currentDelay} - existing coverage was integration-only (Docker-gated, unexecuted in this
 * sandbox). Covers the doctor self-scoping now applied here, mirroring
 * SessionDaySheetControllerTest's own coverage of the identical pattern.
 */
@ExtendWith(MockitoExtension.class)
class SessionDelayServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    private SessionDelayService newService() {
        return new SessionDelayService(sessionRepository, slotRepository, roleAssignmentRepository, doctorProfileRepository);
    }

    private Session mockSession(UUID clinicId, UUID doctorProfileId, ScheduleMode mode, Integer delayMinutes) {
        Clinic clinic = mock(Clinic.class);
        lenient().when(clinic.getId()).thenReturn(clinicId);
        DoctorProfile doctorProfile = mock(DoctorProfile.class);
        lenient().when(doctorProfile.getId()).thenReturn(doctorProfileId);
        Session session = mock(Session.class);
        lenient().when(session.getClinic()).thenReturn(clinic);
        lenient().when(session.getDoctorProfile()).thenReturn(doctorProfile);
        lenient().when(session.getMode()).thenReturn(mode);
        lenient().when(session.getDelayMinutes()).thenReturn(delayMinutes);
        return session;
    }

    @Test
    void callerWithNoActiveRoleAtClinicIsRejected() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> newService().currentDelay(callerAccountId, clinicId, sessionId))
                .isInstanceOf(NotStaffedAtClinicException.class);
    }

    @Test
    void aClinicAdminSeesAnyDoctorsSessionDelay() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RoleAssignment clinicAdminRole = mock(RoleAssignment.class);
        when(clinicAdminRole.getRole()).thenReturn(RoleAssignment.Role.ClinicAdmin);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(clinicAdminRole));
        Session session = mockSession(clinicId, UUID.randomUUID(), ScheduleMode.FIXED_TIME, 12);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        SessionDelayService.SessionDelay result = newService().currentDelay(callerAccountId, clinicId, sessionId);

        assertThat(result.applicable()).isTrue();
        assertThat(result.delayMinutes()).isEqualTo(12);
    }

    @Test
    void aDoctorOnlyCallerSeesTheirOwnSessionDelay() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID doctorProfileId = UUID.randomUUID();
        RoleAssignment doctorRole = mock(RoleAssignment.class);
        when(doctorRole.getRole()).thenReturn(RoleAssignment.Role.Doctor);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(doctorRole));
        DoctorProfile callerDoctorProfile = mock(DoctorProfile.class);
        when(callerDoctorProfile.getId()).thenReturn(doctorProfileId);
        when(doctorProfileRepository.findByAccount_Id(callerAccountId)).thenReturn(Optional.of(callerDoctorProfile));
        Session session = mockSession(clinicId, doctorProfileId, ScheduleMode.FIXED_TIME, 7);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        SessionDelayService.SessionDelay result = newService().currentDelay(callerAccountId, clinicId, sessionId);

        assertThat(result.applicable()).isTrue();
        assertThat(result.delayMinutes()).isEqualTo(7);
    }

    @Test
    void aDoctorOnlyCallerIsRefusedAnotherDoctorsSessionDelay() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID callerDoctorProfileId = UUID.randomUUID();
        UUID otherDoctorProfileId = UUID.randomUUID();
        RoleAssignment doctorRole = mock(RoleAssignment.class);
        when(doctorRole.getRole()).thenReturn(RoleAssignment.Role.Doctor);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(doctorRole));
        DoctorProfile callerDoctorProfile = mock(DoctorProfile.class);
        when(callerDoctorProfile.getId()).thenReturn(callerDoctorProfileId);
        when(doctorProfileRepository.findByAccount_Id(callerAccountId)).thenReturn(Optional.of(callerDoctorProfile));
        Session session = mockSession(clinicId, otherDoctorProfileId, ScheduleMode.FIXED_TIME, 7);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> newService().currentDelay(callerAccountId, clinicId, sessionId))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void aDoctorOnlyCallerWithNoResolvableDoctorProfileFailsClosedWithNotFound() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RoleAssignment doctorRole = mock(RoleAssignment.class);
        when(doctorRole.getRole()).thenReturn(RoleAssignment.Role.Doctor);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(doctorRole));
        when(doctorProfileRepository.findByAccount_Id(callerAccountId)).thenReturn(Optional.empty());
        Session session = mockSession(clinicId, UUID.randomUUID(), ScheduleMode.FIXED_TIME, 7);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> newService().currentDelay(callerAccountId, clinicId, sessionId))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void queueModeSessionIsNotApplicableRegardlessOfDelayField() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RoleAssignment clinicAdminRole = mock(RoleAssignment.class);
        when(clinicAdminRole.getRole()).thenReturn(RoleAssignment.Role.ClinicAdmin);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(clinicAdminRole));
        Session session = mockSession(clinicId, UUID.randomUUID(), ScheduleMode.QUEUE, null);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        SessionDelayService.SessionDelay result = newService().currentDelay(callerAccountId, clinicId, sessionId);

        assertThat(result.applicable()).isFalse();
        assertThat(result.delayMinutes()).isNull();
    }

    @Test
    void sessionNotAtThisClinicIsNotFound() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RoleAssignment clinicAdminRole = mock(RoleAssignment.class);
        when(clinicAdminRole.getRole()).thenReturn(RoleAssignment.Role.ClinicAdmin);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(clinicAdminRole));
        Session session = mockSession(UUID.randomUUID(), UUID.randomUUID(), ScheduleMode.FIXED_TIME, 5);
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> newService().currentDelay(callerAccountId, clinicId, sessionId))
                .isInstanceOf(SessionNotFoundException.class);
    }
}
