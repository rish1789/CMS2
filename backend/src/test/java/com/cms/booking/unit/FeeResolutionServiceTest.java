package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.DoctorDefaultFee;
import com.cms.booking.exception.AppointmentTypeNotFoundException;
import com.cms.booking.exception.NoFeeConfiguredException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.DoctorDefaultFeeRepository;
import com.cms.booking.service.FeeResolutionService;
import com.cms.identity.doctor.DoctorProfile;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 048-backend-unit-tests US1: 017 FR-001..FR-004's override -> default -> hard-block
 * precedence, the exact rule this project's own plan.md/research.md history repeatedly names
 * as easy to silently regress. Pure Mockito - no Spring context, no Docker.
 */
@ExtendWith(MockitoExtension.class)
class FeeResolutionServiceTest {

    @Mock
    private AppointmentTypeRepository appointmentTypeRepository;

    @Mock
    private DoctorDefaultFeeRepository doctorDefaultFeeRepository;

    @Mock
    private AppointmentType appointmentType;

    @Mock
    private DoctorProfile doctorProfile;

    @Mock
    private DoctorDefaultFee doctorDefaultFee;

    private final UUID doctorProfileId = UUID.randomUUID();
    private final UUID appointmentTypeId = UUID.randomUUID();

    private FeeResolutionService newService() {
        return new FeeResolutionService(appointmentTypeRepository, doctorDefaultFeeRepository);
    }

    @Test
    void overrideWinsRegardlessOfDoctorDefault() {
        when(appointmentTypeRepository.findById(appointmentTypeId)).thenReturn(Optional.of(appointmentType));
        when(appointmentType.getDoctorProfile()).thenReturn(doctorProfile);
        when(doctorProfile.getId()).thenReturn(doctorProfileId);
        when(appointmentType.getFeeOverride()).thenReturn(new BigDecimal("750.00"));

        BigDecimal resolved = newService().resolve(doctorProfileId, appointmentTypeId);

        assertThat(resolved).isEqualByComparingTo("750.00");
    }

    @Test
    void defaultFeeUsedWhenNoOverride() {
        when(appointmentTypeRepository.findById(appointmentTypeId)).thenReturn(Optional.of(appointmentType));
        when(appointmentType.getDoctorProfile()).thenReturn(doctorProfile);
        when(doctorProfile.getId()).thenReturn(doctorProfileId);
        when(appointmentType.getFeeOverride()).thenReturn(null);
        when(doctorDefaultFeeRepository.findByDoctorProfile_Id(doctorProfileId)).thenReturn(Optional.of(doctorDefaultFee));
        when(doctorDefaultFee.getAmount()).thenReturn(new BigDecimal("400.00"));

        BigDecimal resolved = newService().resolve(doctorProfileId, appointmentTypeId);

        assertThat(resolved).isEqualByComparingTo("400.00");
    }

    @Test
    void hardBlockWhenNeitherOverrideNorDefaultExists() {
        when(appointmentTypeRepository.findById(appointmentTypeId)).thenReturn(Optional.of(appointmentType));
        when(appointmentType.getDoctorProfile()).thenReturn(doctorProfile);
        when(doctorProfile.getId()).thenReturn(doctorProfileId);
        when(appointmentType.getFeeOverride()).thenReturn(null);
        when(doctorDefaultFeeRepository.findByDoctorProfile_Id(doctorProfileId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().resolve(doctorProfileId, appointmentTypeId))
                .isInstanceOf(NoFeeConfiguredException.class);
    }

    @Test
    void appointmentTypeBelongingToADifferentDoctorIsTreatedAsNotFound() {
        UUID otherDoctorId = UUID.randomUUID();
        when(appointmentTypeRepository.findById(appointmentTypeId)).thenReturn(Optional.of(appointmentType));
        when(appointmentType.getDoctorProfile()).thenReturn(doctorProfile);
        when(doctorProfile.getId()).thenReturn(otherDoctorId);

        assertThatThrownBy(() -> newService().resolve(doctorProfileId, appointmentTypeId))
                .isInstanceOf(AppointmentTypeNotFoundException.class);
    }
}
