package com.cms.clinical.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.cms.clinical.exception.ForbiddenException;
import com.cms.booking.domain.Booking;
import com.cms.booking.exception.BookingNotFoundException;
import com.cms.booking.repository.BookingRepository;
import com.cms.clinical.domain.ConsultationNote;
import com.cms.clinical.repository.ConsultationNoteRepository;
import com.cms.clinical.service.ConsultationNoteService;
import com.cms.clinical.service.TreatingDoctorAuthorizationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 059-patient-clinical-record-access: this service's first pure-Mockito unit tier (existing
 * coverage was integration-only, via ConsultationNoteCreateTest/ConsultationNoteAuthorizationTest).
 * Covers only the new {@code getForPatient} method - {@code create}/{@code get} keep their
 * existing integration-only coverage unchanged.
 */
@ExtendWith(MockitoExtension.class)
class ConsultationNoteServiceTest {

    @Mock
    private TreatingDoctorAuthorizationService treatingDoctorAuthorizationService;

    @Mock
    private ConsultationNoteRepository consultationNoteRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private Booking booking;

    @Mock
    private ConsultationNote note;

    private ConsultationNoteService newService() {
        return new ConsultationNoteService(treatingDoctorAuthorizationService, consultationNoteRepository, bookingRepository);
    }

    @Test
    void returnsTheNoteWhenTheBookingIsTheCallersOwnAndANoteExists() {
        UUID bookingId = UUID.randomUUID();
        UUID patientAccountId = UUID.randomUUID();
        when(bookingRepository.findByIdAndPatient_PatientAccount_Id(bookingId, patientAccountId))
                .thenReturn(Optional.of(booking));
        when(consultationNoteRepository.findByBooking_Id(bookingId)).thenReturn(Optional.of(note));

        Optional<ConsultationNote> result = newService().getForPatient(bookingId, patientAccountId);

        assertThat(result).contains(note);
    }

    @Test
    void returnsEmptyWhenTheBookingIsTheCallersOwnButNoNoteExists() {
        UUID bookingId = UUID.randomUUID();
        UUID patientAccountId = UUID.randomUUID();
        when(bookingRepository.findByIdAndPatient_PatientAccount_Id(bookingId, patientAccountId))
                .thenReturn(Optional.of(booking));
        when(consultationNoteRepository.findByBooking_Id(bookingId)).thenReturn(Optional.empty());

        Optional<ConsultationNote> result = newService().getForPatient(bookingId, patientAccountId);

        assertThat(result).isEmpty();
    }

    @Test
    void throwsBookingNotFoundWhenTheBookingIsNotTheCallersOwn() {
        UUID bookingId = UUID.randomUUID();
        UUID patientAccountId = UUID.randomUUID();
        when(bookingRepository.findByIdAndPatient_PatientAccount_Id(bookingId, patientAccountId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> newService().getForPatient(bookingId, patientAccountId))
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

        assertThatThrownBy(() -> newService().create(clinicId, bookingId, doctorAccountId, "note"))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(consultationNoteRepository);
    }
}
