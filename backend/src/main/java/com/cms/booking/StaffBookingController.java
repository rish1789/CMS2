package com.cms.booking;

import com.cms.booking.dto.BookSlotRequest;
import com.cms.booking.dto.BookingResponse;
import com.cms.identity.account.SecurityConfig;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StaffBookingController {

    private final StaffBookingService staffBookingService;

    public StaffBookingController(StaffBookingService staffBookingService) {
        this.staffBookingService = staffBookingService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/slots/{slotId}/book")
    public ResponseEntity<BookingResponse> book(
            @PathVariable UUID clinicId,
            @PathVariable UUID slotId,
            @Valid @RequestBody BookSlotRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        Booking booking = staffBookingService.bookSlot(
                callerAccountId,
                clinicId,
                slotId,
                new StaffBookingService.BookSlotInput(
                        request.patientId(), request.patientName(), request.patientPhone(), request.appointmentTypeId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.of(booking));
    }
}
