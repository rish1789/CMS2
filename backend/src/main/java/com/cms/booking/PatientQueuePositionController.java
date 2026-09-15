package com.cms.booking;

import com.cms.booking.dto.QueuePositionResponse;
import com.cms.patient.account.SecurityConfig;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 027: ownership of the Booking - not clinic membership - is the access boundary here (research.md R4); no clinicId in the path. */
@RestController
public class PatientQueuePositionController {

    private final BookingRepository bookingRepository;
    private final QueuePositionService queuePositionService;

    public PatientQueuePositionController(BookingRepository bookingRepository, QueuePositionService queuePositionService) {
        this.bookingRepository = bookingRepository;
        this.queuePositionService = queuePositionService;
    }

    @GetMapping("/api/v1/patients/bookings/{bookingId}/queue-position")
    public QueuePositionResponse queuePosition(@PathVariable UUID bookingId, Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);

        Booking booking = bookingRepository
                .findById(bookingId)
                .filter(b -> b.getPatient().getPatientAccount() != null
                        && b.getPatient().getPatientAccount().getId().equals(patientAccountId))
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        QueuePositionService.QueuePosition position = queuePositionService.positionOf(booking);
        return new QueuePositionResponse(bookingId, position.applicable(), position.position());
    }
}
