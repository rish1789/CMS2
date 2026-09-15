package com.cms.booking;

import com.cms.booking.dto.AppointmentTypeResponse;
import com.cms.booking.dto.BookingResponse;
import com.cms.booking.dto.OpenSlotListResponse;
import com.cms.booking.dto.OpenSlotResponse;
import com.cms.booking.dto.PatientBookSlotRequest;
import com.cms.booking.dto.PatientDoctorListResponse;
import com.cms.booking.dto.PatientDoctorSummaryResponse;
import com.cms.booking.dto.QueueSessionListResponse;
import com.cms.booking.dto.QueueSessionResponse;
import com.cms.patient.account.SecurityConfig;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PatientBookingController {

    private final PatientBookingService patientBookingService;

    public PatientBookingController(PatientBookingService patientBookingService) {
        this.patientBookingService = patientBookingService;
    }

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Authentication itself is enforced by the security chain; FR-011 needs no per-caller data
     * beyond that. patient-slot-booking-date-logic: {@code date} drives the date-strip picker -
     * optional, exact-day match; every date (present or absent) is still floored to today-or-later
     * inside the service/repository regardless.
     */
    @GetMapping("/api/v1/patients/clinics/{clinicId}/slots")
    public OpenSlotListResponse listOpenSlots(
            @PathVariable UUID clinicId,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Page<OpenSlotResponse> slotPage =
                patientBookingService.listOpenSlots(clinicId, doctorId, date, PageRequest.of(page, size));
        return new OpenSlotListResponse(slotPage.getContent(), page, size, slotPage.getTotalElements());
    }

    /** patient-booking-flow-rebuild: the Queue-mode analog of {@link #listOpenSlots} - browsing Sessions instead of Slots. */
    @GetMapping("/api/v1/patients/clinics/{clinicId}/queue-sessions")
    public QueueSessionListResponse listQueueSessions(
            @PathVariable UUID clinicId,
            @RequestParam(required = false) UUID doctorId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Page<QueueSessionResponse> sessionPage =
                patientBookingService.listQueueSessions(clinicId, doctorId, PageRequest.of(page, size));
        return new QueueSessionListResponse(sessionPage.getContent(), page, size, sessionPage.getTotalElements());
    }

    /**
     * Doctors staffed at a clinic - backs the doctor picker replacing the raw {@code
     * doctorProfileId} text field on the patient-facing "join waitlist for a specific doctor"
     * form. Authentication itself is enforced by the security chain, same as {@link
     * #listOpenSlots} above; no clinic-membership check on the caller since a patient (unlike
     * staff) is never staffed anywhere.
     */
    @GetMapping("/api/v1/patients/clinics/{clinicId}/doctors")
    public PatientDoctorListResponse listDoctors(
            @PathVariable UUID clinicId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Page<PatientDoctorSummaryResponse> doctorPage =
                patientBookingService.listDoctors(clinicId, q, PageRequest.of(page, size));
        return new PatientDoctorListResponse(doctorPage.getContent(), page, size, doctorPage.getTotalElements());
    }

    /**
     * A doctor's appointment types - backs the picker replacing {@code ClaimOfferCard}'s raw
     * "Appointment Type ID" text field. The only existing listing ({@code BookingController
     * .list}) enforces "this doctor, or a ClinicAdmin staffed at a clinic they work at" - a
     * patient is neither, so it always rejects one. This is the patient-facing equivalent: same
     * no-ownership-check precedent as {@link #listDoctors} above, and the exact same repository
     * read {@link #listOpenSlots}/{@link #listQueueSessions} already perform per-doctor to embed
     * appointment types in their own responses - reused directly rather than re-derived.
     */
    @GetMapping("/api/v1/patients/doctors/{doctorProfileId}/appointment-types")
    public List<AppointmentTypeResponse> listAppointmentTypes(@PathVariable UUID doctorProfileId) {
        return patientBookingService.listAppointmentTypes(doctorProfileId);
    }

    @PostMapping("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book")
    public ResponseEntity<BookingResponse> book(
            @PathVariable UUID clinicId,
            @PathVariable UUID slotId,
            @Valid @RequestBody PatientBookSlotRequest request,
            Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        Booking booking = patientBookingService.bookSlot(
                patientAccountId,
                clinicId,
                slotId,
                new PatientBookingService.BookSlotInput(request.patientName(), request.appointmentTypeId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.of(booking));
    }
}
