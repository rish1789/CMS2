package com.cms.waitlist;

import com.cms.booking.Booking;
import com.cms.booking.dto.BookingResponse;
import com.cms.patient.account.SecurityConfig;
import com.cms.waitlist.dto.ClaimWaitlistRequest;
import com.cms.waitlist.dto.WaitlistEntryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 032 US1/US2: ownership of the Waitlist Entry (not clinic membership) is the access boundary, mirrors 028's booking-cancellation endpoints. */
@RestController
public class PatientWaitlistClaimController {

    private final WaitlistClaimService waitlistClaimService;

    public PatientWaitlistClaimController(WaitlistClaimService waitlistClaimService) {
        this.waitlistClaimService = waitlistClaimService;
    }

    @PostMapping("/api/v1/patients/waitlist-entries/{entryId}/claim")
    public ResponseEntity<BookingResponse> claim(
            @PathVariable UUID entryId, @Valid @RequestBody ClaimWaitlistRequest request, Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        Booking booking = waitlistClaimService.claim(entryId, patientAccountId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.of(booking));
    }

    @PostMapping("/api/v1/patients/waitlist-entries/{entryId}/decline")
    public WaitlistEntryResponse decline(@PathVariable UUID entryId, Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        WaitlistEntry entry = waitlistClaimService.decline(entryId, patientAccountId);
        return WaitlistEntryResponse.of(entry);
    }
}
