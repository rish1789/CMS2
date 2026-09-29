package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.account.domain.Account;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.scheduling.domain.Schedule;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.dto.CreateScheduleRequest;
import com.cms.scheduling.exception.DoctorNotStaffedAtClinicException;
import com.cms.scheduling.exception.ForbiddenException;
import com.cms.scheduling.exception.InvalidScheduleException;
import com.cms.scheduling.exception.ScheduleOverlapException;
import com.cms.scheduling.repository.ScheduleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 048-backend-unit-tests US3: 013/014's validation and cross-clinic overlap-detection rules
 * (ScheduleService's own javadoc: "no Session/Slot write of any kind... structural, not merely
 * tested" - this test protects the *other* half, the business rules it does own). {@code
 * lockDoctorForOverlapCheck}'s Postgres advisory lock stays untested here (inherently a real-
 * database concern, research.md Decision 2) - EntityManager is mocked just enough to let
 * execution proceed past it. Pure Mockito - no Spring context, no Docker.
 */
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ClinicRepository clinicRepository;

    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query nativeQuery;

    @Mock
    private Clinic clinic;

    @Mock
    private DoctorProfile doctorProfile;

    @Mock
    private Account doctorAccount;

    private final UUID callerAccountId = UUID.randomUUID();
    private final UUID clinicId = UUID.randomUUID();
    private final UUID doctorProfileId = UUID.randomUUID();
    private final UUID doctorAccountId = UUID.randomUUID();

    @BeforeEach
    void commonStubs() {
        lenient().when(clinicRepository.findById(clinicId)).thenReturn(Optional.of(clinic));
        lenient().when(doctorProfileRepository.findById(doctorProfileId)).thenReturn(Optional.of(doctorProfile));
        lenient().when(doctorProfile.getAccount()).thenReturn(doctorAccount);
        lenient().when(doctorAccount.getId()).thenReturn(doctorAccountId);
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        lenient().when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
    }

    private com.cms.scheduling.service.ScheduleService newService() {
        return new com.cms.scheduling.service.ScheduleService(
                scheduleRepository, clinicRepository, doctorProfileRepository, roleAssignmentRepository, entityManager);
    }

    private CreateScheduleRequest validFixedTimeRequest() {
        return new CreateScheduleRequest(
                Set.of(DayOfWeek.MONDAY), LocalTime.of(9, 0), LocalTime.of(12, 0), ScheduleMode.FIXED_TIME, 15, null, null);
    }

    private void grantClinicAdmin() {
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin))
                .thenReturn(true);
    }

    private void grantDoctorStaffed() {
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        doctorAccountId, clinicId, RoleAssignment.Role.Doctor))
                .thenReturn(true);
    }

    @Test
    void missingDaysOfWeekIsRejected() {
        grantClinicAdmin();
        CreateScheduleRequest request = new CreateScheduleRequest(
                Set.of(), LocalTime.of(9, 0), LocalTime.of(12, 0), ScheduleMode.FIXED_TIME, 15, null, null);

        assertThatThrownBy(() -> newService().create(callerAccountId, clinicId, doctorProfileId, request))
                .isInstanceOf(InvalidScheduleException.class);
    }

    @Test
    void nonStrictlyIncreasingTimeRangeIsRejected() {
        grantClinicAdmin();
        CreateScheduleRequest request = new CreateScheduleRequest(
                Set.of(DayOfWeek.MONDAY), LocalTime.of(12, 0), LocalTime.of(9, 0), ScheduleMode.FIXED_TIME, 15, null, null);

        assertThatThrownBy(() -> newService().create(callerAccountId, clinicId, doctorProfileId, request))
                .isInstanceOf(InvalidScheduleException.class);
    }

    @Test
    void missingSlotIntervalInFixedTimeModeIsRejected() {
        grantClinicAdmin();
        CreateScheduleRequest request = new CreateScheduleRequest(
                Set.of(DayOfWeek.MONDAY), LocalTime.of(9, 0), LocalTime.of(12, 0), ScheduleMode.FIXED_TIME, null, null, null);

        assertThatThrownBy(() -> newService().create(callerAccountId, clinicId, doctorProfileId, request))
                .isInstanceOf(InvalidScheduleException.class);
    }

    @Test
    void slotIntervalSetInQueueModeIsRejected() {
        grantClinicAdmin();
        CreateScheduleRequest request = new CreateScheduleRequest(
                Set.of(DayOfWeek.MONDAY), LocalTime.of(9, 0), LocalTime.of(12, 0), ScheduleMode.QUEUE, 15, null, null);

        assertThatThrownBy(() -> newService().create(callerAccountId, clinicId, doctorProfileId, request))
                .isInstanceOf(InvalidScheduleException.class);
    }

    @Test
    void callerWithNeitherClinicAdminNorDoctorSelfRoleIsForbidden() {
        // No requireAuthorized stub granted -> both checks default to false.
        assertThatThrownBy(() -> newService().create(callerAccountId, clinicId, doctorProfileId, validFixedTimeRequest()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void doctorNotStaffedAtThisClinicIsRejected() {
        grantClinicAdmin();
        // grantDoctorStaffed() deliberately not called -> existsByAccount_Id...(doctorAccountId,...) is false.
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        doctorAccountId, clinicId, RoleAssignment.Role.Doctor))
                .thenReturn(false);

        assertThatThrownBy(() -> newService().create(callerAccountId, clinicId, doctorProfileId, validFixedTimeRequest()))
                .isInstanceOf(DoctorNotStaffedAtClinicException.class);
    }

    @Test
    void sharedDayWithOverlappingTimeRangeIsRejected() {
        grantClinicAdmin();
        grantDoctorStaffed();
        Schedule existing = new Schedule(
                doctorProfile, clinic, Set.of(DayOfWeek.MONDAY), LocalTime.of(10, 0), LocalTime.of(11, 0), ScheduleMode.FIXED_TIME, 15);
        when(scheduleRepository.findByDoctorProfile_Id(doctorProfileId)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> newService().create(callerAccountId, clinicId, doctorProfileId, validFixedTimeRequest()))
                .isInstanceOf(ScheduleOverlapException.class);
    }

    @Test
    void touchingBoundaryTimeRangeIsAccepted() {
        grantClinicAdmin();
        grantDoctorStaffed();
        // Existing: Mon 07:00-09:00; new request: Mon 09:00-12:00 - touches at 09:00, never overlaps (FR-003).
        Schedule existing = new Schedule(
                doctorProfile, clinic, Set.of(DayOfWeek.MONDAY), LocalTime.of(7, 0), LocalTime.of(9, 0), ScheduleMode.FIXED_TIME, 15);
        when(scheduleRepository.findByDoctorProfile_Id(doctorProfileId)).thenReturn(List.of(existing));
        when(scheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        newService().create(callerAccountId, clinicId, doctorProfileId, validFixedTimeRequest());
        // No exception -> accepted.
    }

    @Test
    void editExcludesItsOwnPriorStateFromTheOverlapComparison() {
        grantClinicAdmin();
        // edit() has no "doctor staffed at clinic" gate (only create() does) - not stubbed here.
        UUID scheduleId = UUID.randomUUID();
        // The schedule being edited is its own only "existing" match - same day/time as the
        // request itself would trivially overlap if not excluded.
        Schedule beingEdited = org.mockito.Mockito.mock(Schedule.class);
        when(beingEdited.getId()).thenReturn(scheduleId);
        when(beingEdited.getClinic()).thenReturn(clinic);
        when(beingEdited.getDoctorProfile()).thenReturn(doctorProfile);
        when(clinic.getId()).thenReturn(clinicId);
        when(doctorProfile.getId()).thenReturn(doctorProfileId);
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(beingEdited));
        when(scheduleRepository.findByDoctorProfile_Id(doctorProfileId)).thenReturn(List.of(beingEdited));
        when(scheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        newService().edit(callerAccountId, clinicId, doctorProfileId, scheduleId, validFixedTimeRequest());
        // No ScheduleOverlapException -> the schedule's own prior state was correctly excluded.
    }

    @Test
    void differentDayNeverOverlapsRegardlessOfTimeRange() {
        grantClinicAdmin();
        grantDoctorStaffed();
        Schedule existing = new Schedule(
                doctorProfile, clinic, Set.of(DayOfWeek.TUESDAY), LocalTime.of(9, 0), LocalTime.of(12, 0), ScheduleMode.FIXED_TIME, 15);
        when(scheduleRepository.findByDoctorProfile_Id(doctorProfileId)).thenReturn(List.of(existing));
        when(scheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        newService().create(callerAccountId, clinicId, doctorProfileId, validFixedTimeRequest());
        // No exception -> accepted.
    }

    @Test
    void onlyOneOfBreakStartOrBreakEndSetIsRejected() {
        grantClinicAdmin();
        CreateScheduleRequest request = new CreateScheduleRequest(
                Set.of(DayOfWeek.MONDAY),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                ScheduleMode.FIXED_TIME,
                15,
                LocalTime.of(14, 0),
                null);

        assertThatThrownBy(() -> newService().create(callerAccountId, clinicId, doctorProfileId, request))
                .isInstanceOf(InvalidScheduleException.class);
    }

    @Test
    void nonStrictlyIncreasingBreakWindowIsRejected() {
        grantClinicAdmin();
        CreateScheduleRequest request = new CreateScheduleRequest(
                Set.of(DayOfWeek.MONDAY),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                ScheduleMode.FIXED_TIME,
                15,
                LocalTime.of(16, 0),
                LocalTime.of(14, 0));

        assertThatThrownBy(() -> newService().create(callerAccountId, clinicId, doctorProfileId, request))
                .isInstanceOf(InvalidScheduleException.class);
    }

    @Test
    void breakWindowOutsideTheScheduleRangeIsRejected() {
        grantClinicAdmin();
        CreateScheduleRequest request = new CreateScheduleRequest(
                Set.of(DayOfWeek.MONDAY),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                ScheduleMode.FIXED_TIME,
                15,
                LocalTime.of(13, 0),
                LocalTime.of(19, 0));

        assertThatThrownBy(() -> newService().create(callerAccountId, clinicId, doctorProfileId, request))
                .isInstanceOf(InvalidScheduleException.class);
    }

    @Test
    void breakWindowInQueueModeIsRejected() {
        grantClinicAdmin();
        CreateScheduleRequest request = new CreateScheduleRequest(
                Set.of(DayOfWeek.MONDAY),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                ScheduleMode.QUEUE,
                null,
                LocalTime.of(14, 0),
                LocalTime.of(16, 0));

        assertThatThrownBy(() -> newService().create(callerAccountId, clinicId, doctorProfileId, request))
                .isInstanceOf(InvalidScheduleException.class);
    }

    @Test
    void validBreakWindowIsAccepted() {
        grantClinicAdmin();
        grantDoctorStaffed();
        when(scheduleRepository.findByDoctorProfile_Id(doctorProfileId)).thenReturn(List.of());
        when(scheduleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CreateScheduleRequest request = new CreateScheduleRequest(
                Set.of(DayOfWeek.MONDAY),
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                ScheduleMode.FIXED_TIME,
                15,
                LocalTime.of(14, 0),
                LocalTime.of(16, 0));

        Schedule saved = newService().create(callerAccountId, clinicId, doctorProfileId, request);

        org.assertj.core.api.Assertions.assertThat(saved.getBreakStartTime()).isEqualTo(LocalTime.of(14, 0));
        org.assertj.core.api.Assertions.assertThat(saved.getBreakEndTime()).isEqualTo(LocalTime.of(16, 0));
    }
}
