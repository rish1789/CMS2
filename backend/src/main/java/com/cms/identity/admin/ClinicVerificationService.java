package com.cms.identity.admin;

import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.admin.dto.BulkDeleteResponse;
import com.cms.identity.admin.dto.BulkRejectResponse;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.inbox.InboxItemRepository;
import com.cms.patient.record.PatientRepository;
import com.cms.scheduling.ScheduleRepository;
import com.cms.scheduling.SessionRepository;
import com.cms.waitlist.WaitlistEntryRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements FR-001..FR-003, FR-007, FR-008: lists clinics by verification state and
 * toggles {@code Clinic.verified} idempotently in either direction, publishing
 * {@link ClinicDeVerifiedEvent} only on a genuine {@code true -> false} transition.
 * Reuses 001's {@link ClinicRepository} directly - no new repository (research.md).
 *
 * <p>super-admin-console-redesign: extended with reject/restore (a reversible bin for a
 * registration that wasn't genuine) and permanent delete (irreversible, Rejected-only, gated
 * on the clinic having no real attached activity - see {@link #deleteGuarded}).
 */
@Service
public class ClinicVerificationService {

    private static final Logger log = LoggerFactory.getLogger(ClinicVerificationService.class);

    private final ClinicRepository clinicRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final InboxItemRepository inboxItemRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final PatientRepository patientRepository;
    private final ScheduleRepository scheduleRepository;
    private final SessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ClinicVerificationService(
            ClinicRepository clinicRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            InboxItemRepository inboxItemRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            PatientRepository patientRepository,
            ScheduleRepository scheduleRepository,
            SessionRepository sessionRepository,
            ApplicationEventPublisher eventPublisher) {
        this.clinicRepository = clinicRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.inboxItemRepository = inboxItemRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.patientRepository = patientRepository;
        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
    }

    private static final String DEFAULT_SORT_FIELD = "createdAt";

    /**
     * super-admin-console-redesign: the queue's unified search/sort/page query. {@code
     * searchTerm} is the caller's raw, unformatted term (or null) - pre-formatted into a
     * lowercased {@code %term%} LIKE pattern here, not by the caller, so every call site gets
     * the CONCAT-with-null fix for free. {@code sortField}/{@code sortDirection} are validated
     * against an allow-list here rather than passed straight into a JPQL property path.
     */
    public Page<Clinic> search(
            String status,
            String searchTerm,
            Clinic.RejectionReason reason,
            String sortField,
            String sortDirection,
            int page,
            int size) {
        if (!status.equals("PENDING") && !status.equals("VERIFIED") && !status.equals("REJECTED")) {
            throw new InvalidListStatusException(status);
        }
        String searchPattern =
                (searchTerm == null || searchTerm.isBlank()) ? null : "%" + searchTerm.toLowerCase() + "%";
        Pageable pageable = PageRequest.of(page, size, resolveSort(sortField, sortDirection));
        return clinicRepository.search(status, searchPattern, reason, pageable);
    }

    /**
     * Deliberately root-level properties only (name/createdAt/rejectedAt all live directly on
     * Clinic) - no cross-entity join to sort by, unlike Doctor's account-name case.
     */
    private Sort resolveSort(String sortField, String sortDirection) {
        String field = sortField == null ? DEFAULT_SORT_FIELD : sortField;
        String property = switch (field) {
            case "name" -> "name";
            case "createdAt" -> "createdAt";
            case "rejectedAt" -> "rejectedAt";
            default -> throw new InvalidSortFieldException(field, "name, createdAt, rejectedAt");
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    @Transactional
    public Clinic verify(UUID clinicId) {
        Clinic clinic = findOrThrow(clinicId);
        if (!clinic.isVerified()) {
            clinic.setVerified(true);
            clinicRepository.save(clinic);
            log.info("Clinic verified: clinicId={}", clinicId);
        }
        return clinic;
    }

    /**
     * Idempotent (FR-007): only a genuine {@code true -> false} transition writes the
     * flag and publishes {@link ClinicDeVerifiedEvent}. A repeated call against an
     * already-unverified clinic is a no-op success with no second event (FR-008).
     */
    @Transactional
    public Clinic unverify(UUID clinicId) {
        Clinic clinic = findOrThrow(clinicId);
        if (clinic.isVerified()) {
            clinic.setVerified(false);
            clinicRepository.save(clinic);
            log.info("Clinic un-verified: clinicId={}", clinicId);
            eventPublisher.publishEvent(ClinicDeVerifiedEvent.of(clinicId));
        }
        return clinic;
    }

    /** super-admin-console-redesign: rejects a single Pending clinic registration as not genuine. Idempotent. */
    @Transactional
    public Clinic reject(UUID clinicId, String reasonValue, String detail, String rejectedBy) {
        Clinic.RejectionReason reason = parseReason(reasonValue);
        Clinic clinic = findOrThrow(clinicId);
        rejectOne(clinic, reason, detail, rejectedBy);
        return clinic;
    }

    /**
     * super-admin-console-redesign: rejects every id in the batch with one shared reason,
     * partial-failure-safe - a stale or already-verified id in the selection fails only that
     * one entry, not the whole batch.
     */
    @Transactional
    public BulkRejectResponse rejectBulk(List<UUID> clinicIds, String reasonValue, String detail, String rejectedBy) {
        Clinic.RejectionReason reason = parseReason(reasonValue);
        List<UUID> succeeded = new ArrayList<>();
        Map<UUID, String> failed = new LinkedHashMap<>();
        for (UUID clinicId : clinicIds) {
            clinicRepository.findById(clinicId).ifPresentOrElse(
                    clinic -> {
                        if (clinic.isVerified()) {
                            failed.put(clinicId, "Already verified - un-verify instead");
                        } else {
                            rejectOne(clinic, reason, detail, rejectedBy);
                            succeeded.add(clinicId);
                        }
                    },
                    () -> failed.put(clinicId, "Not found"));
        }
        log.info("Bulk clinic rejection: succeeded={}, failed={}", succeeded.size(), failed.size());
        return new BulkRejectResponse(succeeded, failed);
    }

    /** super-admin-console-redesign: reverses a rejection, moving the clinic back to Pending. Idempotent. */
    @Transactional
    public Clinic restore(UUID clinicId) {
        Clinic clinic = findOrThrow(clinicId);
        if (clinic.isRejected()) {
            clinic.restore();
            clinicRepository.save(clinic);
            log.info("Clinic restored from rejection: clinicId={}", clinicId);
        }
        return clinic;
    }

    /**
     * super-admin-console-redesign: permanently deletes a single rejected clinic. Throws
     * {@link CannotDeleteUnlessRejectedException} if it isn't rejected, or {@link
     * DeletionBlockedException} naming what's attached if it has real activity - see {@link
     * #deleteGuarded} for the actual gate/cascade.
     */
    @Transactional
    public void delete(UUID clinicId) {
        Clinic clinic = findOrThrow(clinicId);
        if (!clinic.isRejected()) {
            throw new CannotDeleteUnlessRejectedException();
        }
        deleteGuarded(clinic);
    }

    /**
     * super-admin-console-redesign: deletes every id in the batch, partial-failure-safe - a
     * stale id, one that isn't rejected, or one with real activity attached fails only that one
     * entry (its reason recorded), not the whole batch.
     */
    @Transactional
    public BulkDeleteResponse deleteBulk(List<UUID> clinicIds) {
        List<UUID> succeeded = new ArrayList<>();
        Map<UUID, String> failed = new LinkedHashMap<>();
        for (UUID clinicId : clinicIds) {
            clinicRepository.findById(clinicId).ifPresentOrElse(
                    clinic -> {
                        if (!clinic.isRejected()) {
                            failed.put(clinicId, "Not rejected - can only permanently delete a rejected registration");
                            return;
                        }
                        try {
                            deleteGuarded(clinic);
                            succeeded.add(clinicId);
                        } catch (DeletionBlockedException e) {
                            failed.put(clinicId, e.getMessage());
                        }
                    },
                    () -> failed.put(clinicId, "Not found"));
        }
        log.info("Bulk clinic deletion: succeeded={}, failed={}", succeeded.size(), failed.size());
        return new BulkDeleteResponse(succeeded, failed);
    }

    /**
     * super-admin-console-redesign: the auto-purge sweep's candidate set, gated the same way as
     * a manual delete - a clinic with real activity is skipped (logged), not force-deleted, even
     * past its retention window.
     */
    @Transactional
    public int purgeExpiredRejections(Instant cutoff) {
        List<Clinic> candidates = clinicRepository.findByRejectedTrueAndRejectedAtBefore(cutoff);
        int purged = 0;
        for (Clinic clinic : candidates) {
            try {
                deleteGuarded(clinic);
                purged++;
            } catch (DeletionBlockedException e) {
                log.info("Rejection auto-purge skipped clinicId={}: {}", clinic.getId(), e.getMessage());
            }
        }
        return purged;
    }

    /**
     * super-admin-console-redesign: the actual gate + cascade, shared by single delete, bulk
     * delete, and the auto-purge sweep. A clinic already has a real ClinicAdmin Account +
     * RoleAssignment from registration (before Super Admin ever reviews it) - that's pure
     * housekeeping and always cleared. Real usage (a Patient record, a Schedule, a generated
     * Session - nothing gates staff from building these before verification finishes) blocks the
     * delete outright rather than cascading through booking/clinical data that has its own
     * DPDP-retention lifecycle (034).
     */
    private void deleteGuarded(Clinic clinic) {
        UUID clinicId = clinic.getId();
        long patientCount = patientRepository.countByClinic_Id(clinicId);
        long scheduleCount = scheduleRepository.countByClinic_Id(clinicId);
        long sessionCount = sessionRepository.countByClinic_Id(clinicId);
        if (patientCount > 0 || scheduleCount > 0 || sessionCount > 0) {
            throw new DeletionBlockedException(String.format(
                    "Cannot delete: %d patient record(s), %d schedule(s), %d session(s) attached",
                    patientCount, scheduleCount, sessionCount));
        }
        waitlistEntryRepository.deleteByClinic_Id(clinicId);
        inboxItemRepository.deleteByClinic_Id(clinicId);
        roleAssignmentRepository.deleteByClinic_Id(clinicId);
        clinicRepository.delete(clinic);
        log.info("Clinic permanently deleted: clinicId={}", clinicId);
    }

    private void rejectOne(Clinic clinic, Clinic.RejectionReason reason, String detail, String rejectedBy) {
        if (clinic.isVerified()) {
            throw new CannotRejectVerifiedException("Cannot reject an already-verified clinic - un-verify it instead");
        }
        if (!clinic.isRejected()) {
            clinic.reject(reason, detail, rejectedBy);
            clinicRepository.save(clinic);
            log.info("Clinic rejected: clinicId={}, reason={}", clinic.getId(), reason);
        }
    }

    private Clinic.RejectionReason parseReason(String reasonValue) {
        if (reasonValue == null || reasonValue.isBlank()) {
            throw new MissingRejectionReasonException();
        }
        try {
            return Clinic.RejectionReason.valueOf(reasonValue);
        } catch (IllegalArgumentException e) {
            throw new InvalidRejectionReasonException();
        }
    }

    private Clinic findOrThrow(UUID clinicId) {
        return clinicRepository.findById(clinicId).orElseThrow(() -> new ClinicNotFoundException(clinicId));
    }
}
