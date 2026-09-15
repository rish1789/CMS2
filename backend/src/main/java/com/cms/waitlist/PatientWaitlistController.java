package com.cms.waitlist;

import com.cms.patient.account.SecurityConfig;
import com.cms.waitlist.dto.JoinWaitlistRequest;
import com.cms.waitlist.dto.WaitlistEntryResponse;
import jakarta.validation.Valid;
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

/** 031 US2: a patient joins the waitlist for themselves. */
@RestController
public class PatientWaitlistController {

    private final WaitlistJoinService waitlistJoinService;
    private final WaitlistEntryRepository waitlistEntryRepository;

    public PatientWaitlistController(
            WaitlistJoinService waitlistJoinService, WaitlistEntryRepository waitlistEntryRepository) {
        this.waitlistJoinService = waitlistJoinService;
        this.waitlistEntryRepository = waitlistEntryRepository;
    }

    @PostMapping("/api/v1/patients/clinics/{clinicId}/waitlist")
    public ResponseEntity<WaitlistEntryResponse> join(
            @PathVariable UUID clinicId, @Valid @RequestBody JoinWaitlistRequest request, Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        WaitlistEntry entry =
                waitlistJoinService.join(clinicId, patientAccountId, request.doctorProfileId(), request.specialization());
        return ResponseEntity.status(HttpStatus.CREATED).body(WaitlistEntryResponse.of(entry));
    }

    /**
     * _diagnostics [HIGH] - [WAITLIST_CLAIM] - [WORKFLOW_GAP]: the patient's own entries,
     * newest-joined first, so a patient can discover an {@code OFFERED} entry's id to claim it -
     * no endpoint anywhere previously let a patient enumerate their own waitlist entries at all.
     */
    @GetMapping("/api/v1/patients/waitlist-entries")
    public List<WaitlistEntryResponse> listMine(Authentication authentication) {
        UUID patientAccountId = SecurityConfig.currentPatientAccountId(authentication);
        return waitlistEntryRepository.findByPatientAccount_IdOrderByJoinedAtDesc(patientAccountId).stream()
                .map(WaitlistEntryResponse::of)
                .toList();
    }
}
