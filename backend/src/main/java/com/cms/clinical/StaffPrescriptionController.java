package com.cms.clinical;

import com.cms.clinical.dto.CreatePrescriptionRequest;
import com.cms.clinical.dto.PrescriptionResponse;
import com.cms.identity.account.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 035: the booking's treating doctor only - no ClinicAdmin, Operations, or peer-doctor override exists (contracts/prescription.md). */
@RestController
public class StaffPrescriptionController {

    private final PrescriptionService prescriptionService;

    public StaffPrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/bookings/{bookingId}/prescriptions")
    public ResponseEntity<PrescriptionResponse> create(
            @PathVariable UUID clinicId,
            @PathVariable UUID bookingId,
            @RequestBody CreatePrescriptionRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        Prescription prescription = prescriptionService.create(clinicId, bookingId, callerAccountId, request.items());
        return ResponseEntity.status(HttpStatus.CREATED).body(PrescriptionResponse.of(prescription));
    }

    @GetMapping("/api/v1/clinics/{clinicId}/bookings/{bookingId}/prescriptions")
    public List<PrescriptionResponse> list(
            @PathVariable UUID clinicId, @PathVariable UUID bookingId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        return prescriptionService.list(clinicId, bookingId, callerAccountId).stream()
                .map(PrescriptionResponse::of)
                .toList();
    }
}
