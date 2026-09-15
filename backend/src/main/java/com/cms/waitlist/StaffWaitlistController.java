package com.cms.waitlist;

import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.account.SecurityConfig;
import com.cms.waitlist.dto.StaffJoinWaitlistRequest;
import com.cms.waitlist.dto.WaitlistCountResponse;
import com.cms.waitlist.dto.WaitlistEntryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 031 US2: staff joins a patient onto the waitlist on their behalf - Operations-or-ClinicAdmin only, mirrors 025/028/029's standard write-action gate. */
@RestController
public class StaffWaitlistController {

    private final RoleAssignmentRepository roleAssignmentRepository;
    private final WaitlistJoinService waitlistJoinService;
    private final WaitlistEntryRepository waitlistEntryRepository;

    public StaffWaitlistController(
            RoleAssignmentRepository roleAssignmentRepository,
            WaitlistJoinService waitlistJoinService,
            WaitlistEntryRepository waitlistEntryRepository) {
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.waitlistJoinService = waitlistJoinService;
        this.waitlistEntryRepository = waitlistEntryRepository;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/waitlist")
    public ResponseEntity<WaitlistEntryResponse> join(
            @PathVariable UUID clinicId,
            @Valid @RequestBody StaffJoinWaitlistRequest request,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        requireAuthorized(callerAccountId, clinicId);

        WaitlistEntry entry = waitlistJoinService.join(
                clinicId, request.patientAccountId(), request.doctorProfileId(), request.specialization());
        return ResponseEntity.status(HttpStatus.CREATED).body(WaitlistEntryResponse.of(entry));
    }

    /**
     * dashboard-live-data-2026-09-10: the clinic tools dashboard's "waitlist backlog" tile - a
     * read, so it uses the same permissive any-active-role gate as the day sheet/inbox (not
     * {@link #requireAuthorized}'s stricter Operations-or-ClinicAdmin write gate below), since a
     * Doctor landing on the dashboard should see the clinic's waiting count too.
     */
    @GetMapping("/api/v1/clinics/{clinicId}/waitlist/count")
    public WaitlistCountResponse count(@PathVariable UUID clinicId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId)) {
            throw new ForbiddenException();
        }
        long waitingCount = waitlistEntryRepository.countByClinic_IdAndStatus(clinicId, WaitlistEntryStatus.WAITING);
        return new WaitlistCountResponse(waitingCount);
    }

    /** Mirrors 016/020/025/029's identical Operations-or-ClinicAdmin write-action gate. */
    private void requireAuthorized(UUID callerAccountId, UUID clinicId) {
        boolean isOperations = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.Operations);
        boolean isClinicAdmin = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin);

        if (!isOperations && !isClinicAdmin) {
            throw new ForbiddenException();
        }
    }
}
