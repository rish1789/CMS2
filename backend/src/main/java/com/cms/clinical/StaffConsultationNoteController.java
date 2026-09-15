package com.cms.clinical;

import com.cms.clinical.dto.ConsultationNoteResponse;
import com.cms.clinical.dto.CreateConsultationNoteRequest;
import com.cms.identity.account.SecurityConfig;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 034: the booking's treating doctor only - no ClinicAdmin or peer-doctor override exists (contracts/consultation-note.md). */
@RestController
public class StaffConsultationNoteController {

    private final ConsultationNoteService consultationNoteService;

    public StaffConsultationNoteController(ConsultationNoteService consultationNoteService) {
        this.consultationNoteService = consultationNoteService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes")
    public ResponseEntity<ConsultationNoteResponse> create(
            @PathVariable UUID clinicId,
            @PathVariable UUID bookingId,
            @RequestBody CreateConsultationNoteRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        ConsultationNote note = consultationNoteService.create(clinicId, bookingId, callerAccountId, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(ConsultationNoteResponse.of(note));
    }

    @GetMapping("/api/v1/clinics/{clinicId}/bookings/{bookingId}/consultation-notes")
    public ConsultationNoteResponse get(
            @PathVariable UUID clinicId, @PathVariable UUID bookingId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        ConsultationNote note = consultationNoteService.get(clinicId, bookingId, callerAccountId);
        return ConsultationNoteResponse.of(note);
    }
}
