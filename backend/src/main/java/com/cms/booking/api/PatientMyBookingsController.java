package com.cms.booking.api;

import com.cms.booking.domain.Booking;
import com.cms.booking.repository.BookingRepository;


import com.cms.booking.dto.PatientBookingListResponse;
import com.cms.booking.dto.PatientBookingSummaryResponse;
import com.cms.booking.exception.BookingNotFoundException;
import com.cms.booking.service.PatientVisitOutcomes;
import com.cms.patient.account.config.SecurityConfig;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * patient-booking-flow-rebuild: "My bookings" - every Booking (any status, any clinic) the
 * caller has made, replacing the "type a Booking ID" entry point on the patient dashboard.
 */
@RestController
public class PatientMyBookingsController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final BookingRepository bookingRepository;
    private final PatientVisitOutcomes patientVisitOutcomes;

    public PatientMyBookingsController(BookingRepository bookingRepository, PatientVisitOutcomes patientVisitOutcomes) {
        this.bookingRepository = bookingRepository;
        this.patientVisitOutcomes = patientVisitOutcomes;
    }

    @GetMapping("/api/v1/patients/bookings")
    public PatientBookingListResponse mine(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        Page<Booking> bookingPage =
                bookingRepository.findByPatient_PatientAccount_IdOrderByCreatedAtDesc(patientAccountId, PageRequest.of(page, size));
        var bookings = bookingPage.getContent().stream().map(this::summary).toList();
        return new PatientBookingListResponse(bookings, page, size, bookingPage.getTotalElements());
    }

    /**
     * 069-patient-visit-outcomes FR-003: one booking, with its outcome and cancellation
     * eligibility. Ownership of the Booking is the access boundary - anyone else gets the same
     * 404 as an unknown id, exactly like the live-status and cancel endpoints.
     */
    @GetMapping("/api/v1/patients/bookings/{bookingId}")
    public PatientBookingSummaryResponse one(@PathVariable UUID bookingId, Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        Booking booking = bookingRepository
                .findById(bookingId)
                .filter(b -> b.getPatient().getPatientAccount() != null
                        && b.getPatient().getPatientAccount().getId().equals(patientAccountId))
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        return summary(booking);
    }

    private PatientBookingSummaryResponse summary(Booking booking) {
        return PatientBookingSummaryResponse.of(
                booking, patientVisitOutcomes.outcome(booking), patientVisitOutcomes.eligibility(booking));
    }
}
