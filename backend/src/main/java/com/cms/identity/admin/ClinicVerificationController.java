package com.cms.identity.admin;

import com.cms.identity.admin.dto.BulkDeleteRequest;
import com.cms.identity.admin.dto.BulkDeleteResponse;
import com.cms.identity.admin.dto.BulkRejectRequest;
import com.cms.identity.admin.dto.BulkRejectResponse;
import com.cms.identity.admin.dto.ClinicListResponse;
import com.cms.identity.admin.dto.ClinicSummaryResponse;
import com.cms.identity.admin.dto.RejectRequest;
import com.cms.identity.admin.dto.VerificationStatusResponse;
import com.cms.identity.clinic.Clinic;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Implements contracts/clinic-verification.md. Every path here sits behind {@link SuperAdminSecurityConfig}. */
@RestController
@RequestMapping("/api/v1/admin/clinics")
public class ClinicVerificationController {

    private final ClinicVerificationService clinicVerificationService;

    public ClinicVerificationController(ClinicVerificationService clinicVerificationService) {
        this.clinicVerificationService = clinicVerificationService;
    }

    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * super-admin-console-redesign: {@code status} replaced the old boolean {@code verified}
     * param, coordinated with the frontend in the same change - PENDING/VERIFIED/REJECTED,
     * not just two states, now that rejection exists alongside verification. {@code q} is a
     * free-text search (name/address/contact), {@code reason} filters the Rejected tab by
     * rejection reason, and {@code sort}/{@code direction} sort the result - all added for the
     * same reason: the queue is expected to hold hundreds to thousands of clinics at scale.
     */
    @GetMapping
    public ClinicListResponse list(
            @RequestParam String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Clinic.RejectionReason reason,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Page<Clinic> clinicPage = clinicVerificationService.search(status, q, reason, sort, direction, page, size);
        var clinics = clinicPage.getContent().stream().map(ClinicSummaryResponse::from).toList();
        return new ClinicListResponse(clinics, page, size, clinicPage.getTotalElements());
    }

    @PostMapping("/{clinicId}/verify")
    public VerificationStatusResponse verify(@PathVariable UUID clinicId) {
        Clinic clinic = clinicVerificationService.verify(clinicId);
        return new VerificationStatusResponse(clinic.getId(), clinic.isVerified());
    }

    @PostMapping("/{clinicId}/unverify")
    public VerificationStatusResponse unverify(@PathVariable UUID clinicId) {
        Clinic clinic = clinicVerificationService.unverify(clinicId);
        return new VerificationStatusResponse(clinic.getId(), clinic.isVerified());
    }

    /** super-admin-console-redesign: rejects a single Pending clinic registration as not genuine, with a required reason. */
    @PostMapping("/{clinicId}/reject")
    public ClinicSummaryResponse reject(
            @PathVariable UUID clinicId, @RequestBody RejectRequest request, Authentication authentication) {
        String rejectedBy = SuperAdminSecurityConfig.currentSuperAdminUsername(authentication);
        Clinic clinic = clinicVerificationService.reject(clinicId, request.reasonCode(), request.detail(), rejectedBy);
        return ClinicSummaryResponse.from(clinic);
    }

    /** super-admin-console-redesign: rejects a batch of Pending clinics with one shared reason. */
    @PostMapping("/reject-bulk")
    public BulkRejectResponse rejectBulk(@RequestBody BulkRejectRequest request, Authentication authentication) {
        String rejectedBy = SuperAdminSecurityConfig.currentSuperAdminUsername(authentication);
        return clinicVerificationService.rejectBulk(request.ids(), request.reasonCode(), request.detail(), rejectedBy);
    }

    /** super-admin-console-redesign: reverses a rejection, moving the clinic back to Pending. */
    @PostMapping("/{clinicId}/restore")
    public ClinicSummaryResponse restore(@PathVariable UUID clinicId) {
        Clinic clinic = clinicVerificationService.restore(clinicId);
        return ClinicSummaryResponse.from(clinic);
    }

    /**
     * super-admin-console-redesign: permanently deletes a single rejected clinic - Rejected-only
     * (enforced server-side, not just hidden from the UI elsewhere) and refused with a clear
     * message if real activity is attached (see ClinicVerificationService.deleteGuarded).
     */
    @DeleteMapping("/{clinicId}")
    public void delete(@PathVariable UUID clinicId) {
        clinicVerificationService.delete(clinicId);
    }

    /** super-admin-console-redesign: permanently deletes a batch of rejected clinics. */
    @PostMapping("/delete-bulk")
    public BulkDeleteResponse deleteBulk(@RequestBody BulkDeleteRequest request) {
        return clinicVerificationService.deleteBulk(request.ids());
    }
}
