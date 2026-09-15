package com.cms.booking;

import com.cms.booking.dto.PatientBookingListResponse;
import com.cms.booking.dto.PatientBookingSummaryResponse;
import com.cms.patient.account.SecurityConfig;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
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

    public PatientMyBookingsController(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @GetMapping("/api/v1/patients/bookings")
    public PatientBookingListResponse mine(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        Page<Booking> bookingPage =
                bookingRepository.findByPatient_PatientAccount_IdOrderByCreatedAtDesc(patientAccountId, PageRequest.of(page, size));
        var bookings = bookingPage.getContent().stream().map(PatientBookingSummaryResponse::of).toList();
        return new PatientBookingListResponse(bookings, page, size, bookingPage.getTotalElements());
    }
}
