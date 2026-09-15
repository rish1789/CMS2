package com.cms.identity.admin;

import com.cms.identity.admin.dto.BulkDeleteRequest;
import com.cms.identity.admin.dto.BulkDeleteResponse;
import com.cms.identity.admin.dto.BulkRejectRequest;
import com.cms.identity.admin.dto.BulkRejectResponse;
import com.cms.identity.admin.dto.DoctorProfileListResponse;
import com.cms.identity.admin.dto.DoctorProfileSummaryResponse;
import com.cms.identity.admin.dto.DoctorVerificationStatusResponse;
import com.cms.identity.admin.dto.EditDoctorProfileRequest;
import com.cms.identity.admin.dto.RejectRequest;
import com.cms.identity.doctor.DoctorProfile;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Implements contracts/doctor-verification.md. Sits behind {@link SuperAdminSecurityConfig}'s existing /api/v1/admin/** matcher. */
@RestController
@RequestMapping("/api/v1/admin/doctors")
public class DoctorVerificationController {

    private final DoctorVerificationService doctorVerificationService;

    public DoctorVerificationController(DoctorVerificationService doctorVerificationService) {
        this.doctorVerificationService = doctorVerificationService;
    }

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * super-admin-console-redesign: {@code status} replaced the old boolean {@code verified}
     * param, coordinated with the frontend in the same change - PENDING/VERIFIED/REJECTED,
     * not just two states, now that rejection exists alongside verification. {@code q} is a
     * free-text search (name/email/specialization/license), {@code reason} filters the
     * Rejected tab by rejection reason, and {@code sort}/{@code direction} sort the result -
     * all added for the same reason: the queue is expected to hold hundreds to thousands of
     * doctors at scale.
     */
    @GetMapping
    public DoctorProfileListResponse list(
            @RequestParam String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DoctorProfile.RejectionReason reason,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Page<DoctorProfile> doctorPage =
                doctorVerificationService.search(status, q, reason, sort, direction, page, size);
        var doctors = doctorPage.getContent().stream().map(DoctorProfileSummaryResponse::from).toList();
        return new DoctorProfileListResponse(doctors, page, size, doctorPage.getTotalElements());
    }

    @PostMapping("/{doctorProfileId}/verify")
    public DoctorVerificationStatusResponse verify(@PathVariable UUID doctorProfileId) {
        DoctorProfile profile = doctorVerificationService.verify(doctorProfileId);
        return new DoctorVerificationStatusResponse(profile.getId(), profile.isLicenseVerified());
    }

    /** 033: the explicit revoke action - contracts/doctor-revoke.md. */
    @PostMapping("/{doctorProfileId}/revoke")
    public DoctorVerificationStatusResponse revoke(@PathVariable UUID doctorProfileId) {
        DoctorProfile profile = doctorVerificationService.revoke(doctorProfileId);
        return new DoctorVerificationStatusResponse(profile.getId(), profile.isLicenseVerified());
    }

    @PatchMapping("/{doctorProfileId}")
    public DoctorProfileSummaryResponse edit(
            @PathVariable UUID doctorProfileId, @Valid @RequestBody EditDoctorProfileRequest request) {
        DoctorProfile profile = doctorVerificationService.edit(doctorProfileId, request);
        return DoctorProfileSummaryResponse.from(profile);
    }

    /** super-admin-console-redesign: rejects a single Pending queue entry as not genuine, with a required reason. */
    @PostMapping("/{doctorProfileId}/reject")
    public DoctorProfileSummaryResponse reject(
            @PathVariable UUID doctorProfileId, @RequestBody RejectRequest request, Authentication authentication) {
        String rejectedBy = SuperAdminSecurityConfig.currentSuperAdminUsername(authentication);
        DoctorProfile profile =
                doctorVerificationService.reject(doctorProfileId, request.reasonCode(), request.detail(), rejectedBy);
        return DoctorProfileSummaryResponse.from(profile);
    }

    /** super-admin-console-redesign: rejects a batch of Pending doctor profiles with one shared reason. */
    @PostMapping("/reject-bulk")
    public BulkRejectResponse rejectBulk(@RequestBody BulkRejectRequest request, Authentication authentication) {
        String rejectedBy = SuperAdminSecurityConfig.currentSuperAdminUsername(authentication);
        return doctorVerificationService.rejectBulk(request.ids(), request.reasonCode(), request.detail(), rejectedBy);
    }

    /** super-admin-console-redesign: reverses a rejection, moving the profile back to Pending. */
    @PostMapping("/{doctorProfileId}/restore")
    public DoctorProfileSummaryResponse restore(@PathVariable UUID doctorProfileId) {
        DoctorProfile profile = doctorVerificationService.restore(doctorProfileId);
        return DoctorProfileSummaryResponse.from(profile);
    }

    /**
     * super-admin-console-redesign: permanently deletes a single rejected doctor profile -
     * Rejected-only (enforced server-side) and refused with a clear message if real activity is
     * attached (see DoctorVerificationService.deleteGuarded).
     */
    @DeleteMapping("/{doctorProfileId}")
    public void delete(@PathVariable UUID doctorProfileId) {
        doctorVerificationService.delete(doctorProfileId);
    }

    /** super-admin-console-redesign: permanently deletes a batch of rejected doctor profiles. */
    @PostMapping("/delete-bulk")
    public BulkDeleteResponse deleteBulk(@RequestBody BulkDeleteRequest request) {
        return doctorVerificationService.deleteBulk(request.ids());
    }
}
