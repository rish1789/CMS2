package com.cms.booking;

import com.cms.booking.dto.QueueBookSlotRequest;
import com.cms.booking.dto.QueueBookingResponse;
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
public class StaffQueueBookingController {

    private final StaffQueueBookingService staffQueueBookingService;

    public StaffQueueBookingController(StaffQueueBookingService staffQueueBookingService) {
        this.staffQueueBookingService = staffQueueBookingService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/sessions/{sessionId}/queue-bookings")
    public ResponseEntity<QueueBookingResponse> book(
            @PathVariable UUID clinicId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody QueueBookSlotRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        Booking booking = staffQueueBookingService.bookSlot(
                callerAccountId,
                clinicId,
                sessionId,
                new StaffQueueBookingService.BookSlotInput(
                        request.patientId(), request.patientName(), request.patientPhone(), request.appointmentTypeId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(QueueBookingResponse.of(booking));
    }
}
