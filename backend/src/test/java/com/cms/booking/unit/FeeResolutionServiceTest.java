package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.ClinicAppointmentTypePrice;
import com.cms.booking.domain.ClinicDoctorFee;
import com.cms.booking.exception.AppointmentTypeNotFoundException;
import com.cms.booking.exception.NoFeeConfiguredException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.ClinicAppointmentTypePriceRepository;
import com.cms.booking.repository.ClinicDoctorFeeRepository;
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
 * 048-backend-unit-tests US1, re-scoped by 068-per-clinic-fees: the override -> default ->
 * hard-block precedence (017 FR-001..FR-004, backlog 015) now resolves from the booking's
 * clinic only - a price at another clinic, or the retired doctor-wide fields, is never used
 * (068 FR-003/FR-004/FR-012). Pure Mockito - no Spring context, no Docker.
 */
@ExtendWith(MockitoExtension.class)
class FeeResolutionServiceTest {

    @Mock
    private AppointmentTypeRepository appointmentTypeRepository;

    @Mock
    private ClinicDoctorFeeRepository clinicDoctorFeeRepository;

    @Mock
    private ClinicAppointmentTypePriceRepository clinicAppointmentTypePriceRepository;

    @Mock
    private AppointmentType appointmentType;

    @Mock
    private DoctorProfile doctorProfile;

    @Mock
    private ClinicDoctorFee clinicDoctorFee;

    @Mock
    private ClinicAppointmentTypePrice clinicTypePrice;

    private final UUID clinicId = UUID.randomUUID();
    private final UUID doctorProfileId = UUID.randomUUID();
    private final UUID appointmentTypeId = UUID.randomUUID();

    private FeeResolutionService newService() {
        return new FeeResolutionService(
                appointmentTypeRepository, clinicDoctorFeeRepository, clinicAppointmentTypePriceRepository);
    }

    private void typeBelongsToDoctor() {
        when(appointmentTypeRepository.findById(appointmentTypeId)).thenReturn(Optional.of(appointmentType));
        when(appointmentType.getDoctorProfile()).thenReturn(doctorProfile);
        when(doctorProfile.getId()).thenReturn(doctorProfileId);
    }

    @Test
    void clinicTypePriceWinsOverTheClinicDefault() {
        typeBelongsToDoctor();
        when(clinicAppointmentTypePriceRepository.findByClinic_IdAndAppointmentType_Id(clinicId, appointmentTypeId))
                .thenReturn(Optional.of(clinicTypePrice));
        when(clinicTypePrice.getAmount()).thenReturn(new BigDecimal("750.00"));
        lenient()
                .when(clinicDoctorFeeRepository.findByClinic_IdAndDoctorProfile_Id(clinicId, doctorProfileId))
                .thenReturn(Optional.of(clinicDoctorFee));

        BigDecimal resolved = newService().resolve(clinicId, doctorProfileId, appointmentTypeId);

        assertThat(resolved).isEqualByComparingTo("750.00");
    }

    @Test
    void clinicDefaultUsedWhenTheTypeHasNoPriceAtThisClinic() {
        typeBelongsToDoctor();
        when(clinicAppointmentTypePriceRepository.findByClinic_IdAndAppointmentType_Id(clinicId, appointmentTypeId))
                .thenReturn(Optional.empty());
        when(clinicDoctorFeeRepository.findByClinic_IdAndDoctorProfile_Id(clinicId, doctorProfileId))
                .thenReturn(Optional.of(clinicDoctorFee));
        when(clinicDoctorFee.getAmount()).thenReturn(new BigDecimal("400.00"));

        BigDecimal resolved = newService().resolve(clinicId, doctorProfileId, appointmentTypeId);

        assertThat(resolved).isEqualByComparingTo("400.00");
    }

    /**
     * 068 FR-004: only this clinic's rows are ever looked up - the repositories are queried by
     * (clinicId, ...) and return nothing here, so another clinic's prices cannot leak in.
     */
    @Test
    void hardBlockWhenThisClinicHasNoPriceEvenIfAnotherClinicDoes() {
        typeBelongsToDoctor();
        when(clinicAppointmentTypePriceRepository.findByClinic_IdAndAppointmentType_Id(clinicId, appointmentTypeId))
                .thenReturn(Optional.empty());
        when(clinicDoctorFeeRepository.findByClinic_IdAndDoctorProfile_Id(clinicId, doctorProfileId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().resolve(clinicId, doctorProfileId, appointmentTypeId))
                .isInstanceOf(NoFeeConfiguredException.class);
    }

    /** 068 FR-012: the retired doctor-wide fee_override on the type is never used to resolve. */
    @Test
    void theRetiredDoctorWideOverrideIsIgnored() {
        typeBelongsToDoctor();
        lenient().when(appointmentType.getFeeOverride()).thenReturn(new BigDecimal("999.00"));
        when(clinicAppointmentTypePriceRepository.findByClinic_IdAndAppointmentType_Id(clinicId, appointmentTypeId))
                .thenReturn(Optional.empty());
        when(clinicDoctorFeeRepository.findByClinic_IdAndDoctorProfile_Id(clinicId, doctorProfileId))
                .thenReturn(Optional.of(clinicDoctorFee));
        when(clinicDoctorFee.getAmount()).thenReturn(new BigDecimal("400.00"));

        BigDecimal resolved = newService().resolve(clinicId, doctorProfileId, appointmentTypeId);

        assertThat(resolved).isEqualByComparingTo("400.00");
    }

    @Test
    void appointmentTypeBelongingToADifferentDoctorIsTreatedAsNotFound() {
        UUID otherDoctorId = UUID.randomUUID();
        when(appointmentTypeRepository.findById(appointmentTypeId)).thenReturn(Optional.of(appointmentType));
        when(appointmentType.getDoctorProfile()).thenReturn(doctorProfile);
        when(doctorProfile.getId()).thenReturn(otherDoctorId);

        assertThatThrownBy(() -> newService().resolve(clinicId, doctorProfileId, appointmentTypeId))
                .isInstanceOf(AppointmentTypeNotFoundException.class);
    }
}
