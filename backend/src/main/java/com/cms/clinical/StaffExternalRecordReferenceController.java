package com.cms.clinical;

import com.cms.clinical.dto.CreateExternalRecordReferenceRequest;
import com.cms.clinical.dto.ExternalRecordReferenceResponse;
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

/** 036: the booking's treating doctor only - no ClinicAdmin, Operations, or peer-doctor override exists (contracts/external-record-reference.md). */
@RestController
public class StaffExternalRecordReferenceController {

    private final ExternalRecordReferenceService externalRecordReferenceService;

    public StaffExternalRecordReferenceController(ExternalRecordReferenceService externalRecordReferenceService) {
        this.externalRecordReferenceService = externalRecordReferenceService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references")
    public ResponseEntity<ExternalRecordReferenceResponse> create(
            @PathVariable UUID clinicId,
            @PathVariable UUID bookingId,
            @RequestBody CreateExternalRecordReferenceRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        ExternalRecordReference reference = externalRecordReferenceService.create(
                clinicId,
                bookingId,
                callerAccountId,
                request.recordType(),
                request.sourceProvider(),
                request.recordDate(),
                request.summary());
        return ResponseEntity.status(HttpStatus.CREATED).body(ExternalRecordReferenceResponse.of(reference));
    }

    @GetMapping("/api/v1/clinics/{clinicId}/bookings/{bookingId}/external-record-references")
    public List<ExternalRecordReferenceResponse> list(
            @PathVariable UUID clinicId, @PathVariable UUID bookingId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        return externalRecordReferenceService.list(clinicId, bookingId, callerAccountId).stream()
                .map(ExternalRecordReferenceResponse::of)
                .toList();
    }
}
