package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cms.booking.dto.DoctorBookingReadinessResponse;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.ClinicAppointmentTypePriceRepository;
import com.cms.booking.repository.ClinicDoctorFeeRepository;
import com.cms.booking.service.DoctorBookingReadinessService;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * real-bug-fix 2026-09-17: pure Mockito, no Spring context - proves both the original scenario
 * found live (Kamlesh Rawat: zero AppointmentTypes, no default fee) and the false-positive it
 * was initially catching too (Gauresh Kumar: AppointmentTypes present, each with its own
 * feeOverride, no default fee needed or set) are both handled correctly. 068-per-clinic-fees:
 * both checks now read the clinic's own prices (FR-010).
 */
@ExtendWith(MockitoExtension.class)
class DoctorBookingReadinessServiceTest {

    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    @Mock
    private AppointmentTypeRepository appointmentTypeRepository;

    @Mock
    private ClinicDoctorFeeRepository clinicDoctorFeeRepository;

    @Mock
    private ClinicAppointmentTypePriceRepository clinicAppointmentTypePriceRepository;

    private final UUID clinicId = UUID.randomUUID();
    private final UUID readyDoctorId = UUID.randomUUID();
    private final UUID incompleteDoctorId = UUID.randomUUID();

    private DoctorBookingReadinessService newService() {
        return new DoctorBookingReadinessService(
                doctorProfileRepository, appointmentTypeRepository, clinicDoctorFeeRepository, clinicAppointmentTypePriceRepository);
    }

    private DoctorProfile mockDoctor(UUID id) {
        DoctorProfile profile = mock(DoctorProfile.class);
        when(profile.getId()).thenReturn(id);
        return profile;
    }

    @Test
    void flagsADoctorWithNoAppointmentTypesAndNoDefaultFeeAsNotBookingReady() {
        Page<DoctorProfile> page = new PageImpl<>(List.of(mockDoctor(readyDoctorId), mockDoctor(incompleteDoctorId)));
        when(doctorProfileRepository.findByClinicStaffed(any(), any(), any())).thenReturn(page);
        when(appointmentTypeRepository.findDoctorProfileIdsWithAppointmentTypes(List.of(readyDoctorId, incompleteDoctorId)))
                .thenReturn(List.of(readyDoctorId));
        when(clinicDoctorFeeRepository.findDoctorProfileIdsWithDefaultFeeAtClinic(clinicId, List.of(readyDoctorId, incompleteDoctorId)))
                .thenReturn(List.of(readyDoctorId));
        when(clinicAppointmentTypePriceRepository.findDoctorProfileIdsWithAnAppointmentTypeUnpricedAtClinic(
                        clinicId, List.of(readyDoctorId, incompleteDoctorId)))
                .thenReturn(List.of());

        List<DoctorBookingReadinessResponse> result = newService().forClinic(clinicId);

        DoctorBookingReadinessResponse ready =
                result.stream().filter(r -> r.doctorProfileId().equals(readyDoctorId)).findFirst().orElseThrow();
        DoctorBookingReadinessResponse incomplete =
                result.stream().filter(r -> r.doctorProfileId().equals(incompleteDoctorId)).findFirst().orElseThrow();

        assertThat(ready.hasAppointmentTypes()).isTrue();
        assertThat(ready.hasDefaultFee()).isTrue();
        assertThat(ready.isBookingReady()).isTrue();

        assertThat(incomplete.hasAppointmentTypes()).isFalse();
        assertThat(incomplete.hasDefaultFee()).isFalse();
        assertThat(incomplete.isBookingReady()).isFalse();
    }

    @Test
    void aDoctorWithAnAppointmentTypeMissingAnOverrideAndNoDefaultFeeIsFlaggedNotReady() {
        Page<DoctorProfile> page = new PageImpl<>(List.of(mockDoctor(incompleteDoctorId)));
        when(doctorProfileRepository.findByClinicStaffed(any(), any(), any())).thenReturn(page);
        when(appointmentTypeRepository.findDoctorProfileIdsWithAppointmentTypes(List.of(incompleteDoctorId)))
                .thenReturn(List.of(incompleteDoctorId));
        when(clinicDoctorFeeRepository.findDoctorProfileIdsWithDefaultFeeAtClinic(clinicId, List.of(incompleteDoctorId)))
                .thenReturn(List.of());
        when(clinicAppointmentTypePriceRepository.findDoctorProfileIdsWithAnAppointmentTypeUnpricedAtClinic(
                        clinicId, List.of(incompleteDoctorId)))
                .thenReturn(List.of(incompleteDoctorId));

        List<DoctorBookingReadinessResponse> result = newService().forClinic(clinicId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hasAppointmentTypes()).isTrue();
        assertThat(result.get(0).hasDefaultFee()).isFalse();
        assertThat(result.get(0).hasAppointmentTypeMissingFeeOverride()).isTrue();
        assertThat(result.get(0).isBookingReady()).isFalse();
    }

    // real-bug-fix 2026-09-17: the actual live-found false positive - a doctor (Gauresh Kumar)
    // whose every AppointmentType already carries its own feeOverride is fully bookable despite
    // having no default fee, since FeeResolutionService never needs it for a type that already
    // resolves its own fee. An earlier version of this feature flagged this doctor incorrectly.
    @Test
    void aDoctorWhoseAppointmentTypesAllHaveTheirOwnFeeOverrideIsBookingReadyDespiteNoDefaultFee() {
        Page<DoctorProfile> page = new PageImpl<>(List.of(mockDoctor(readyDoctorId)));
        when(doctorProfileRepository.findByClinicStaffed(any(), any(), any())).thenReturn(page);
        when(appointmentTypeRepository.findDoctorProfileIdsWithAppointmentTypes(List.of(readyDoctorId)))
                .thenReturn(List.of(readyDoctorId));
        when(clinicDoctorFeeRepository.findDoctorProfileIdsWithDefaultFeeAtClinic(clinicId, List.of(readyDoctorId)))
                .thenReturn(List.of());
        when(clinicAppointmentTypePriceRepository.findDoctorProfileIdsWithAnAppointmentTypeUnpricedAtClinic(
                        clinicId, List.of(readyDoctorId)))
                .thenReturn(List.of());

        List<DoctorBookingReadinessResponse> result = newService().forClinic(clinicId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hasAppointmentTypes()).isTrue();
        assertThat(result.get(0).hasDefaultFee()).isFalse();
        assertThat(result.get(0).hasAppointmentTypeMissingFeeOverride()).isFalse();
        assertThat(result.get(0).isBookingReady()).isTrue();
    }

    @Test
    void aClinicWithNoDoctorsReturnsAnEmptyListWithoutQueryingEitherRepository() {
        when(doctorProfileRepository.findByClinicStaffed(any(), any(), any())).thenReturn(Page.empty());

        List<DoctorBookingReadinessResponse> result = newService().forClinic(clinicId);

        assertThat(result).isEmpty();
    }
}
