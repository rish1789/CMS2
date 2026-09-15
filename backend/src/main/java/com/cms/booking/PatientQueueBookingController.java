package com.cms.booking;

import com.cms.booking.dto.PatientQueueBookSlotRequest;
import com.cms.booking.dto.QueueBookingResponse;
import com.cms.patient.account.SecurityConfig;
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
public class PatientQueueBookingController {

    private final PatientQueueBookingService patientQueueBookingService;

    public PatientQueueBookingController(PatientQueueBookingService patientQueueBookingService) {
        this.patientQueueBookingService = patientQueueBookingService;
    }

    @PostMapping("/api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings")
    public ResponseEntity<QueueBookingResponse> book(
            @PathVariable UUID clinicId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody PatientQueueBookSlotRequest request,
            Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        Booking booking = patientQueueBookingService.bookSlot(
                patientAccountId,
                clinicId,
                sessionId,
                new PatientQueueBookingService.BookSlotInput(request.patientName(), request.appointmentTypeId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(QueueBookingResponse.of(booking));
    }
}
