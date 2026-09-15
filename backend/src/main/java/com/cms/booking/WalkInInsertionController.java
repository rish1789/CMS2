package com.cms.booking;

import com.cms.booking.dto.BookingResponse;
import com.cms.booking.dto.WalkInRequest;
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
public class WalkInInsertionController {

    private final WalkInInsertionService walkInInsertionService;

    public WalkInInsertionController(WalkInInsertionService walkInInsertionService) {
        this.walkInInsertionService = walkInInsertionService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/sessions/{sessionId}/walk-in")
    public ResponseEntity<BookingResponse> insertWalkIn(
            @PathVariable UUID clinicId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody WalkInRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        Booking booking = walkInInsertionService.insertWalkIn(
                callerAccountId,
                clinicId,
                sessionId,
                new WalkInInsertionService.WalkInInsertionInput(
                        request.patientId(),
                        request.patientName(),
                        request.patientPhone(),
                        request.appointmentTypeId(),
                        request.overrideReason()));
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.of(booking));
    }
}
