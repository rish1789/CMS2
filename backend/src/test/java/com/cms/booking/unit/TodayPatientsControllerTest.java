package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cms.booking.api.TodayPatientsController;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingSource;
import com.cms.booking.dto.TodayPatientResponse;
import com.cms.booking.repository.BookingRepository;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.identity.account.domain.Account;
import com.cms.patient.record.domain.Patient;
import com.cms.booking.exception.NotStaffedAtClinicException;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * real-bug-fix 2026-09-17: pure Mockito, no Spring context - mirrors
 * TodaySessionStatsControllerTest's own "any active role at this clinic" gate pattern.
 *
 * <p>doctor-console-cross-doctor-leak fix: also covers the doctor self-scoping now applied
 * here, mirroring TodaySessionStatsControllerTest's own coverage of the same pattern.
 */
@ExtendWith(MockitoExtension.class)
class TodayPatientsControllerTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    private TodayPatientsController newController() {
        return new TodayPatientsController(bookingRepository, roleAssignmentRepository, doctorProfileRepository);
    }

    private static UsernamePasswordAuthenticationToken authenticationFor(UUID accountId) {
        return new UsernamePasswordAuthenticationToken(accountId, null);
    }

    private Booking mockBooking(ScheduleMode mode, BookingSource source, SlotStatus slotStatus) {
        Account doctorAccount = mock(Account.class);
        when(doctorAccount.getName()).thenReturn("Dr. Asha Rao");
        DoctorProfile doctorProfile = mock(DoctorProfile.class);
        when(doctorProfile.getId()).thenReturn(UUID.randomUUID());
        when(doctorProfile.getAccount()).thenReturn(doctorAccount);
        Session session = mock(Session.class);
        when(session.getDoctorProfile()).thenReturn(doctorProfile);
        when(session.getMode()).thenReturn(mode);
        Slot slot = mock(Slot.class);
        when(slot.getSession()).thenReturn(session);
        when(slot.getStartTime()).thenReturn(mode == ScheduleMode.FIXED_TIME ? LocalTime.of(9, 0) : null);
        when(slot.getTokenNumber()).thenReturn(mode == ScheduleMode.QUEUE ? 3 : null);
        when(slot.getStatus()).thenReturn(slotStatus);
        Patient patient = mock(Patient.class);
        when(patient.getId()).thenReturn(UUID.randomUUID());
        when(patient.getName()).thenReturn("Asha Patient");
        when(patient.getPhone()).thenReturn("9876543210");
        Booking booking = mock(Booking.class);
        when(booking.getId()).thenReturn(UUID.randomUUID());
        when(booking.getSlot()).thenReturn(slot);
        when(booking.getPatient()).thenReturn(patient);
        when(booking.getSource()).thenReturn(source);
        return booking;
    }

    @Test
    void listsTodaysActiveBookingsAcrossFixedTimeAndQueueModes() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        RoleAssignment clinicAdminRole = mock(RoleAssignment.class);
        when(clinicAdminRole.getRole()).thenReturn(RoleAssignment.Role.ClinicAdmin);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(clinicAdminRole));
        Booking scheduled = mockBooking(ScheduleMode.FIXED_TIME, BookingSource.SCHEDULED, SlotStatus.BOOKED);
        Booking walkIn = mockBooking(ScheduleMode.QUEUE, BookingSource.WALK_IN, SlotStatus.BOOKED);
        when(bookingRepository.findActiveByClinicAndSessionDate(eq(clinicId), any(), isNull()))
                .thenReturn(List.of(scheduled, walkIn));

        List<TodayPatientResponse> response = newController().today(clinicId, authenticationFor(callerAccountId));

        assertThat(response).hasSize(2);
        assertThat(response.get(0).isWalkIn()).isFalse();
        assertThat(response.get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.get(1).isWalkIn()).isTrue();
        assertThat(response.get(1).tokenNumber()).isEqualTo(3);
        assertThat(response).allMatch(r -> r.doctorName().equals("Dr. Asha Rao"));
        assertThat(response).allMatch(r -> r.patientName().equals("Asha Patient"));
    }

    @Test
    void callerWithNoActiveRoleAtClinicIsRejected() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> newController().today(clinicId, authenticationFor(callerAccountId)))
                .isInstanceOf(NotStaffedAtClinicException.class);
    }

    @Test
    void doctorOnlyCallerSeesOnlyTheirOwnPatients() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID doctorProfileId = UUID.randomUUID();
        RoleAssignment doctorRole = mock(RoleAssignment.class);
        when(doctorRole.getRole()).thenReturn(RoleAssignment.Role.Doctor);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(doctorRole));
        DoctorProfile callerDoctorProfile = mock(DoctorProfile.class);
        when(callerDoctorProfile.getId()).thenReturn(doctorProfileId);
        when(doctorProfileRepository.findByAccount_Id(callerAccountId)).thenReturn(Optional.of(callerDoctorProfile));
        Booking ownBooking = mockBooking(ScheduleMode.FIXED_TIME, BookingSource.SCHEDULED, SlotStatus.BOOKED);
        when(bookingRepository.findActiveByClinicAndSessionDate(eq(clinicId), any(), eq(doctorProfileId)))
                .thenReturn(List.of(ownBooking));

        List<TodayPatientResponse> response = newController().today(clinicId, authenticationFor(callerAccountId));

        assertThat(response).hasSize(1);
    }

    @Test
    void doctorOnlyCallerWithNoResolvableDoctorProfileFailsClosedToAnEmptyList() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        RoleAssignment doctorRole = mock(RoleAssignment.class);
        when(doctorRole.getRole()).thenReturn(RoleAssignment.Role.Doctor);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(doctorRole));
        when(doctorProfileRepository.findByAccount_Id(callerAccountId)).thenReturn(Optional.empty());

        List<TodayPatientResponse> response = newController().today(clinicId, authenticationFor(callerAccountId));

        assertThat(response).isEmpty();
    }
}
