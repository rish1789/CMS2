package com.cms.clinical.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.cms.clinical.exception.ForbiddenException;
import com.cms.booking.domain.Booking;
import com.cms.booking.exception.BookingNotFoundException;
import com.cms.booking.repository.BookingRepository;
import com.cms.clinical.domain.Prescription;
import com.cms.clinical.repository.PrescriptionRepository;
import com.cms.clinical.service.PrescriptionService;
import com.cms.clinical.service.TreatingDoctorAuthorizationService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 059-patient-clinical-record-access: this service's first pure-Mockito unit tier (existing
 * coverage was integration-only). Covers only the new {@code listForPatient} method -
 * {@code create}/{@code list} keep their existing integration-only coverage unchanged.
 */
@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {

    @Mock
    private TreatingDoctorAuthorizationService treatingDoctorAuthorizationService;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private Booking booking;

    @Mock
    private Prescription prescription;

    private PrescriptionService newService() {
        return new PrescriptionService(treatingDoctorAuthorizationService, prescriptionRepository, bookingRepository);
    }

    @Test
    void returnsEveryPrescriptionWhenTheBookingIsTheCallersOwn() {
        UUID bookingId = UUID.randomUUID();
        UUID patientAccountId = UUID.randomUUID();
        when(bookingRepository.findByIdAndPatient_PatientAccount_Id(bookingId, patientAccountId))
                .thenReturn(Optional.of(booking));
        when(prescriptionRepository.findByBooking_Id(bookingId)).thenReturn(List.of(prescription));

        List<Prescription> result = newService().listForPatient(bookingId, patientAccountId);

        assertThat(result).containsExactly(prescription);
    }

    @Test
    void returnsAnEmptyListWhenTheBookingIsTheCallersOwnButNoneExist() {
        UUID bookingId = UUID.randomUUID();
        UUID patientAccountId = UUID.randomUUID();
        when(bookingRepository.findByIdAndPatient_PatientAccount_Id(bookingId, patientAccountId))
                .thenReturn(Optional.of(booking));
        when(prescriptionRepository.findByBooking_Id(bookingId)).thenReturn(List.of());

        List<Prescription> result = newService().listForPatient(bookingId, patientAccountId);

        assertThat(result).isEmpty();
    }

    @Test
    void throwsBookingNotFoundWhenTheBookingIsNotTheCallersOwn() {
        UUID bookingId = UUID.randomUUID();
        UUID patientAccountId = UUID.randomUUID();
        when(bookingRepository.findByIdAndPatient_PatientAccount_Id(bookingId, patientAccountId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().listForPatient(bookingId, patientAccountId))
                .isInstanceOf(BookingNotFoundException.class);
    }

    /** 065-phase1-stabilization (SEC-06): creation requires an active Doctor role at the clinic, and nothing is written when refused. */
    @Test
    void createIsRefusedWhenTheTreatingDoctorsRoleAtTheClinicIsNoLongerActive() {
        UUID clinicId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID doctorAccountId = UUID.randomUUID();
        when(treatingDoctorAuthorizationService.findBookingInClinic(clinicId, bookingId)).thenReturn(booking);
        when(treatingDoctorAuthorizationService.requireActiveTreatingDoctor(booking, doctorAccountId))
                .thenThrow(new ForbiddenException());

        assertThatThrownBy(() -> newService().create(clinicId, bookingId, doctorAccountId, java.util.List.of(new com.cms.clinical.dto.PrescriptionItemRequest("Paracetamol", "500mg", "TDS", "3 days", null))))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(prescriptionRepository);
    }
}
