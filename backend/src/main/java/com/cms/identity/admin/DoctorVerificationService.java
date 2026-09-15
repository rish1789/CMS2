package com.cms.identity.admin;

import com.cms.booking.AppointmentTypeRepository;
import com.cms.booking.DoctorDefaultFeeRepository;
import com.cms.identity.admin.dto.BulkDeleteResponse;
import com.cms.identity.admin.dto.BulkRejectResponse;
import com.cms.identity.admin.dto.EditDoctorProfileRequest;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
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
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements FR-004..FR-007 (005/007) plus 008's edit action (FR-001..FR-007): lists
 * Doctor Profiles by license-verification state, idempotently flips {@code licenseVerified}
 * true, and lets Super Admin edit a profile's fields. Mirrors {@code ClinicVerificationService}
 * (003) exactly (research.md).
 *
 * <p>Extended by 033-deverification-cascade-auto-cancel: {@link #revoke} is the explicit,
 * intentional two-way-toggle counterpart to {@link #verify} that this class's own javadoc
 * previously said had no requirement to exist - it now does, as the necessary prerequisite for
 * that feature's Trigger 2. Deliberately separate from {@link #edit}'s own automatic
 * verification reset (006), which continues to never publish an event or trigger a cascade.
 */
@Service
public class DoctorVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DoctorVerificationService.class);

    private final DoctorProfileRepository doctorProfileRepository;
    private final ScheduleRepository scheduleRepository;
    private final SessionRepository sessionRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final DoctorDefaultFeeRepository doctorDefaultFeeRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public DoctorVerificationService(
            DoctorProfileRepository doctorProfileRepository,
            ScheduleRepository scheduleRepository,
            SessionRepository sessionRepository,
            AppointmentTypeRepository appointmentTypeRepository,
            DoctorDefaultFeeRepository doctorDefaultFeeRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            ApplicationEventPublisher eventPublisher) {
        this.doctorProfileRepository = doctorProfileRepository;
        this.scheduleRepository = scheduleRepository;
        this.sessionRepository = sessionRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.doctorDefaultFeeRepository = doctorDefaultFeeRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
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
    public Page<DoctorProfile> search(
            String status,
            String searchTerm,
            DoctorProfile.RejectionReason reason,
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
        return doctorProfileRepository.search(status, searchPattern, reason, pageable);
    }

    /**
     * Deliberately root-level properties only, all living directly on DoctorProfile - no
     * "sort by doctor name" option, since that would need to traverse the Account association
     * and Spring Data's Sort-append behavior across a join in a custom @Query is not something
     * this sandbox can verify against a real Hibernate/Postgres stack (Docker unavailable) -
     * not worth the risk of an unverified cross-entity ORDER BY reaching production.
     */
    private Sort resolveSort(String sortField, String sortDirection) {
        String field = sortField == null ? DEFAULT_SORT_FIELD : sortField;
        String property = switch (field) {
            case "specialization" -> "specialization";
            case "licenseNumber" -> "licenseNumber";
            case "experienceYears" -> "experienceYears";
            case "createdAt" -> "createdAt";
            case "rejectedAt" -> "rejectedAt";
            default -> throw new InvalidSortFieldException(
                    field, "specialization, licenseNumber, experienceYears, createdAt, rejectedAt");
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    @Transactional
    public DoctorProfile verify(UUID doctorProfileId) {
        DoctorProfile profile = findOrThrow(doctorProfileId);
        if (!profile.isLicenseVerified()) {
            profile.setLicenseVerified(true);
            doctorProfileRepository.save(profile);
            log.info("Doctor license verified: doctorProfileId={}", doctorProfileId);
        }
        return profile;
    }

    /**
     * 033 FR-002/FR-003/FR-009: the explicit, intentional revoke action - only a genuine
     * {@code true -> false} transition writes the flag and publishes
     * {@link DoctorLicenseRevokedEvent}, mirroring {@code ClinicVerificationService.unverify}'s
     * identical idempotent shape exactly. Never called by {@link #edit}'s own automatic reset.
     */
    @Transactional
    public DoctorProfile revoke(UUID doctorProfileId) {
        DoctorProfile profile = findOrThrow(doctorProfileId);
        if (profile.isLicenseVerified()) {
            profile.setLicenseVerified(false);
            doctorProfileRepository.save(profile);
            log.info("Doctor license revoked: doctorProfileId={}", doctorProfileId);
            eventPublisher.publishEvent(DoctorLicenseRevokedEvent.of(doctorProfileId));
        }
        return profile;
    }

    /**
     * 008 FR-001..FR-007: edits all four editable fields together. If the license number is
     * actually changing and the profile was verified beforehand, resets {@code licenseVerified}
     * to false in this same transaction (FR-003) - no event is published (research.md), so this
     * reset structurally cannot trigger 008-deverification-cascade-auto-cancel-bookings' cascade
     * (FR-006). A resulting {@code uq_doctor_profile_license_number} violation (the edited
     * license number now collides with a different profile) is translated to
     * {@link DuplicateLicenseNumberException} (FR-007).
     */
    @Transactional
    public DoctorProfile edit(UUID doctorProfileId, EditDoctorProfileRequest request) {
        DoctorProfile profile = findOrThrow(doctorProfileId);

        boolean licenseNumberChanged = !profile.getLicenseNumber().equals(request.licenseNumber());
        boolean shouldResetVerification = licenseNumberChanged && profile.isLicenseVerified();

        profile.setSpecialization(request.specialization());
        profile.setLicenseNumber(request.licenseNumber());
        profile.setExperienceYears(request.experienceYears());
        profile.setVisible(request.visible());
        if (shouldResetVerification) {
            profile.setLicenseVerified(false);
        }

        try {
            profile = doctorProfileRepository.save(profile);
        } catch (DataAccessException e) {
            log.warn("Doctor profile edit failed: {}", e.getClass().getSimpleName());
            if (isUniqueConstraintViolation(e, "uq_doctor_profile_license_number")) {
                throw new DuplicateLicenseNumberException();
            }
            throw e;
        }

        log.info(
                "Doctor profile edited: doctorProfileId={}, licenseNumberChanged={}, verificationReset={}",
                doctorProfileId,
                licenseNumberChanged,
                shouldResetVerification);
        return profile;
    }

    /** super-admin-console-redesign: rejects a single Pending license-verification queue entry as not genuine. Idempotent. */
    @Transactional
    public DoctorProfile reject(UUID doctorProfileId, String reasonValue, String detail, String rejectedBy) {
        DoctorProfile.RejectionReason reason = parseReason(reasonValue);
        DoctorProfile profile = findOrThrow(doctorProfileId);
        rejectOne(profile, reason, detail, rejectedBy);
        return profile;
    }

    /**
     * super-admin-console-redesign: rejects every id in the batch with one shared reason,
     * partial-failure-safe - a stale or already-verified id in the selection fails only that
     * one entry, not the whole batch.
     */
    @Transactional
    public BulkRejectResponse rejectBulk(
            List<UUID> doctorProfileIds, String reasonValue, String detail, String rejectedBy) {
        DoctorProfile.RejectionReason reason = parseReason(reasonValue);
        List<UUID> succeeded = new ArrayList<>();
        Map<UUID, String> failed = new LinkedHashMap<>();
        for (UUID doctorProfileId : doctorProfileIds) {
            doctorProfileRepository.findById(doctorProfileId).ifPresentOrElse(
                    profile -> {
                        if (profile.isLicenseVerified()) {
                            failed.put(doctorProfileId, "Already verified - revoke instead");
                        } else {
                            rejectOne(profile, reason, detail, rejectedBy);
                            succeeded.add(doctorProfileId);
                        }
                    },
                    () -> failed.put(doctorProfileId, "Not found"));
        }
        log.info("Bulk doctor rejection: succeeded={}, failed={}", succeeded.size(), failed.size());
        return new BulkRejectResponse(succeeded, failed);
    }

    /** super-admin-console-redesign: reverses a rejection, moving the profile back to Pending. Idempotent. */
    @Transactional
    public DoctorProfile restore(UUID doctorProfileId) {
        DoctorProfile profile = findOrThrow(doctorProfileId);
        if (profile.isRejected()) {
            profile.restore();
            doctorProfileRepository.save(profile);
            log.info("Doctor profile restored from rejection: doctorProfileId={}", doctorProfileId);
        }
        return profile;
    }

    /**
     * super-admin-console-redesign: permanently deletes a single rejected doctor profile.
     * Throws {@link CannotDeleteUnlessRejectedException} if it isn't rejected, or {@link
     * DeletionBlockedException} naming what's attached if it has real activity.
     */
    @Transactional
    public void delete(UUID doctorProfileId) {
        DoctorProfile profile = findOrThrow(doctorProfileId);
        if (!profile.isRejected()) {
            throw new CannotDeleteUnlessRejectedException();
        }
        deleteGuarded(profile);
    }

    /**
     * super-admin-console-redesign: deletes every id in the batch, partial-failure-safe - a
     * stale id, one that isn't rejected, or one with real activity attached fails only that one
     * entry (its reason recorded), not the whole batch.
     */
    @Transactional
    public BulkDeleteResponse deleteBulk(List<UUID> doctorProfileIds) {
        List<UUID> succeeded = new ArrayList<>();
        Map<UUID, String> failed = new LinkedHashMap<>();
        for (UUID doctorProfileId : doctorProfileIds) {
            doctorProfileRepository.findById(doctorProfileId).ifPresentOrElse(
                    profile -> {
                        if (!profile.isRejected()) {
                            failed.put(
                                    doctorProfileId,
                                    "Not rejected - can only permanently delete a rejected registration");
                            return;
                        }
                        try {
                            deleteGuarded(profile);
                            succeeded.add(doctorProfileId);
                        } catch (DeletionBlockedException e) {
                            failed.put(doctorProfileId, e.getMessage());
                        }
                    },
                    () -> failed.put(doctorProfileId, "Not found"));
        }
        log.info("Bulk doctor deletion: succeeded={}, failed={}", succeeded.size(), failed.size());
        return new BulkDeleteResponse(succeeded, failed);
    }

    /**
     * super-admin-console-redesign: the auto-purge sweep's candidate set, gated the same way as
     * a manual delete - a profile with real activity is skipped (logged), not force-deleted,
     * even past its retention window.
     */
    @Transactional
    public int purgeExpiredRejections(Instant cutoff) {
        List<DoctorProfile> candidates = doctorProfileRepository.findByRejectedTrueAndRejectedAtBefore(cutoff);
        int purged = 0;
        for (DoctorProfile profile : candidates) {
            try {
                deleteGuarded(profile);
                purged++;
            } catch (DeletionBlockedException e) {
                log.info("Rejection auto-purge skipped doctorProfileId={}: {}", profile.getId(), e.getMessage());
            }
        }
        return purged;
    }

    /**
     * super-admin-console-redesign: the actual gate + delete, shared by single delete, bulk
     * delete, and the auto-purge sweep. Unlike a Clinic, a DoctorProfile has no housekeeping row
     * that's always created alongside it - nothing here gates on {@code licenseVerified}, so a
     * ClinicAdmin can build a real Schedule/Session/AppointmentType/default fee for an unverified
     * doctor before Super Admin ever reviews it. If any of that (or a standing waitlist entry)
     * exists, block rather than cascade through it - that data has its own DPDP-retention
     * lifecycle (034), not something a "remove a fraudulent request" cleanup action should eat.
     */
    private void deleteGuarded(DoctorProfile profile) {
        UUID doctorProfileId = profile.getId();
        long scheduleCount = scheduleRepository.countByDoctorProfile_Id(doctorProfileId);
        long sessionCount = sessionRepository.countByDoctorProfile_Id(doctorProfileId);
        long appointmentTypeCount = appointmentTypeRepository.countByDoctorProfile_Id(doctorProfileId);
        boolean hasDefaultFee = doctorDefaultFeeRepository.existsByDoctorProfile_Id(doctorProfileId);
        long waitlistCount = waitlistEntryRepository.countByDoctorProfile_Id(doctorProfileId);
        if (scheduleCount > 0 || sessionCount > 0 || appointmentTypeCount > 0 || hasDefaultFee || waitlistCount > 0) {
            throw new DeletionBlockedException(String.format(
                    "Cannot delete: %d schedule(s), %d session(s), %d appointment type(s)%s, %d waitlist entr%s attached",
                    scheduleCount,
                    sessionCount,
                    appointmentTypeCount,
                    hasDefaultFee ? ", a default fee" : "",
                    waitlistCount,
                    waitlistCount == 1 ? "y" : "ies"));
        }
        waitlistEntryRepository.deleteByDoctorProfile_Id(doctorProfileId);
        doctorProfileRepository.delete(profile);
        log.info("Doctor profile permanently deleted: doctorProfileId={}", doctorProfileId);
    }

    private void rejectOne(DoctorProfile profile, DoctorProfile.RejectionReason reason, String detail, String rejectedBy) {
        if (profile.isLicenseVerified()) {
            throw new CannotRejectVerifiedException("Cannot reject an already-verified doctor - revoke instead");
        }
        if (!profile.isRejected()) {
            profile.reject(reason, detail, rejectedBy);
            doctorProfileRepository.save(profile);
            log.info("Doctor profile rejected: doctorProfileId={}, reason={}", profile.getId(), reason);
        }
    }

    private DoctorProfile.RejectionReason parseReason(String reasonValue) {
        if (reasonValue == null || reasonValue.isBlank()) {
            throw new MissingRejectionReasonException();
        }
        try {
            return DoctorProfile.RejectionReason.valueOf(reasonValue);
        } catch (IllegalArgumentException e) {
            throw new InvalidRejectionReasonException();
        }
    }

    private DoctorProfile findOrThrow(UUID doctorProfileId) {
        return doctorProfileRepository
                .findById(doctorProfileId)
                .orElseThrow(() -> new DoctorProfileNotFoundException(doctorProfileId));
    }

    private boolean isUniqueConstraintViolation(DataAccessException e, String constraintName) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
