package com.cms.protection.service;

import com.cms.booking.domain.BookingAttemptOutcome;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.booking.repository.BookingRepository;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.protection.domain.SuspiciousActivityFlag;
import com.cms.protection.domain.SuspiciousActivityFlagStatus;
import com.cms.protection.dto.FlagDetailResponse;
import com.cms.protection.dto.FlagListResponse;
import com.cms.protection.dto.FlagResponse;
import com.cms.protection.dto.RecentActivityResponse;
import com.cms.protection.exception.FlagNotFoundException;
import com.cms.protection.exception.ProtectionForbiddenException;
import com.cms.protection.repository.SuspiciousActivityFlagRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 060-booking-abuse-prevention (spec.md FR-021-FR-025, research.md Decision 7): clinic-scoped
 * flag review, requiring ClinicAdmin specifically at the target clinic - reusing the exact
 * `existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue` check {@code ScheduleService} already
 * established for this same "must be ClinicAdmin" gate.
 */
@Service
public class ClinicProtectionFlagService {

    private static final int RECENT_ACTIVITY_LIMIT = 10;

    private final SuspiciousActivityFlagRepository flagRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final BookingRepository bookingRepository;
    private final BookingAttemptLogRepository attemptLogRepository;
    private final ProtectionSettingService protectionSettingService;

    public ClinicProtectionFlagService(
            SuspiciousActivityFlagRepository flagRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            BookingRepository bookingRepository,
            BookingAttemptLogRepository attemptLogRepository,
            ProtectionSettingService protectionSettingService) {
        this.flagRepository = flagRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.bookingRepository = bookingRepository;
        this.attemptLogRepository = attemptLogRepository;
        this.protectionSettingService = protectionSettingService;
    }

    private void requireClinicAdmin(UUID callerAccountId, UUID clinicId) {
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin)) {
            throw new ProtectionForbiddenException();
        }
    }

    /** FR-021/FR-025: this clinic's own flags only, optionally filtered by status/patient. */
    public FlagListResponse list(
            UUID callerAccountId, UUID clinicId, SuspiciousActivityFlagStatus status, UUID patientAccountId, int page, int size) {
        requireClinicAdmin(callerAccountId, clinicId);
        Pageable pageable = PageRequest.of(page, size);
        Page<SuspiciousActivityFlag> result;
        if (status != null && patientAccountId != null) {
            result = flagRepository.findByClinic_IdAndStatusAndPatientAccount_Id(clinicId, status, patientAccountId, pageable);
        } else if (status != null) {
            result = flagRepository.findByClinic_IdAndStatus(clinicId, status, pageable);
        } else if (patientAccountId != null) {
            result = flagRepository.findByClinic_IdAndPatientAccount_Id(clinicId, patientAccountId, pageable);
        } else {
            result = flagRepository.findByClinic_Id(clinicId, pageable);
        }
        return new FlagListResponse(
                result.getContent().stream().map(FlagResponse::of).toList(), page, size, result.getTotalElements());
    }

    /** FR-021/FR-022: flag detail with this clinic's own evidence, plus the one cross-clinic fact. */
    public FlagDetailResponse detail(UUID callerAccountId, UUID clinicId, UUID flagId) {
        requireClinicAdmin(callerAccountId, clinicId);
        SuspiciousActivityFlag flag =
                flagRepository.findByIdAndClinic_Id(flagId, clinicId).orElseThrow(() -> new FlagNotFoundException(flagId));

        UUID patientAccountId = flag.getPatientAccount().getId();
        Pageable top10 = PageRequest.of(0, RECENT_ACTIVITY_LIMIT);

        var recentBookings = bookingRepository
                .findTop10ByPatient_PatientAccount_IdAndPatient_Clinic_IdAndStatusOrderByCreatedAtDesc(
                        patientAccountId, clinicId, BookingStatus.ACTIVE)
                .stream()
                .map(RecentActivityResponse.BookingSummary::of)
                .toList();
        var recentCancellations = bookingRepository.findRecentCancellations(patientAccountId, clinicId, top10).stream()
                .map(RecentActivityResponse.BookingSummary::of)
                .toList();
        var recentNoShows = bookingRepository.findRecentNoShows(patientAccountId, clinicId, top10).stream()
                .map(RecentActivityResponse.BookingSummary::of)
                .toList();
        var rateLimitViolations = attemptLogRepository
                .findTop10ByPatientAccount_IdAndClinic_IdAndOutcomeOrderByAttemptedAtDesc(
                        patientAccountId, clinicId, BookingAttemptOutcome.RATE_LIMITED)
                .stream()
                .map(a -> new RecentActivityResponse.RateLimitViolation(a.getAttemptedAt()))
                .toList();

        long globalCount = bookingRepository.countByPatient_PatientAccount_IdAndStatus(patientAccountId, BookingStatus.ACTIVE);
        boolean atGlobalLimit = globalCount >= protectionSettingService.getGlobalMaxActiveAppointments();

        return new FlagDetailResponse(
                FlagResponse.of(flag),
                new RecentActivityResponse(
                        recentBookings, recentCancellations, recentNoShows, rateLimitViolations, globalCount, atGlobalLimit));
    }

    /** FR-023: mark an outstanding flag reviewed/resolved. */
    @Transactional
    public FlagResponse resolve(UUID callerAccountId, UUID clinicId, UUID flagId, String resolvedBy) {
        requireClinicAdmin(callerAccountId, clinicId);
        SuspiciousActivityFlag flag =
                flagRepository.findByIdAndClinic_Id(flagId, clinicId).orElseThrow(() -> new FlagNotFoundException(flagId));
        flag.resolve(Instant.now(), resolvedBy);
        return FlagResponse.of(flag);
    }
}
