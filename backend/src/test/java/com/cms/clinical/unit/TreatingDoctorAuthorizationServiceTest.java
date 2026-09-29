package com.cms.clinical.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.Booking;
import com.cms.booking.repository.BookingRepository;
import com.cms.clinical.exception.ForbiddenException;
import com.cms.clinical.service.TreatingDoctorAuthorizationService;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.doctor.DoctorProfile;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 065-phase1-stabilization US5 (SEC-06 / PB-008): creating clinical documentation requires the
 * treating doctor to still hold an active Doctor role at the booking's clinic. Reading keeps the
 * original treating-doctor-only rule (historical visibility, spec 034), unchanged.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TreatingDoctorAuthorizationServiceTest {

    private final UUID doctorAccountId = UUID.randomUUID();
    private final UUID clinicId = UUID.randomUUID();

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Booking booking;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DoctorProfile treatingDoctor;

    private TreatingDoctorAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new TreatingDoctorAuthorizationService(bookingRepository, roleAssignmentRepository);
        when(treatingDoctor.getAccount().getId()).thenReturn(doctorAccountId);
        when(booking.getSlot().getSession().getDoctorProfile()).thenReturn(treatingDoctor);
        when(booking.getSlot().getSession().getClinic().getId()).thenReturn(clinicId);
    }

    private void doctorRoleActive(boolean active) {
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        doctorAccountId, clinicId, RoleAssignment.Role.Doctor))
                .thenReturn(active);
    }

    @Test
    void treatingDoctorWithAnActiveDoctorRoleMayCreate() {
        doctorRoleActive(true);

        assertThat(service.requireActiveTreatingDoctor(booking, doctorAccountId)).isSameAs(treatingDoctor);
    }

    @Test
    void treatingDoctorWhoseDoctorRoleAtTheClinicIsInactiveMayNotCreate() {
        doctorRoleActive(false);

        assertThatThrownBy(() -> service.requireActiveTreatingDoctor(booking, doctorAccountId))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aDifferentDoctorMayNotCreateEvenWithAnActiveRole() {
        UUID otherDoctor = UUID.randomUUID();
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(any(), any(), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.requireActiveTreatingDoctor(booking, otherDoctor))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void readingStaysGovernedByTheTreatingDoctorRuleOnlyForHistoricalVisibility() {
        doctorRoleActive(false);

        assertThat(service.requireTreatingDoctor(booking, doctorAccountId)).isSameAs(treatingDoctor);
        verify(roleAssignmentRepository, never())
                .existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(any(), any(), any());
    }
}
