package com.cms.booking.service;

import com.cms.booking.domain.ClinicBookingLimitOverride;
import com.cms.booking.domain.ClinicBookingLimitOverrideChangeLog;
import com.cms.booking.dto.ClinicBookingLimitOverrideHistoryEntryResponse;
import com.cms.booking.dto.ClinicBookingLimitOverrideResponse;
import com.cms.booking.exception.ClinicLimitExceedsGlobalCapException;
import com.cms.booking.exception.ClinicProtectionForbiddenException;
import com.cms.booking.repository.ClinicBookingLimitOverrideChangeLogRepository;
import com.cms.booking.repository.ClinicBookingLimitOverrideRepository;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.protection.service.ProtectionSettingService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 060-booking-abuse-prevention (spec.md FR-004/FR-028, US5, BR-005, AUD-003): the optional
 * per-clinic supplementary appointment limit, ClinicAdmin-only at that specific clinic (research.md
 * Decision 7's reused gate).
 */
@Service
public class ClinicBookingLimitOverrideService {

    private final ClinicBookingLimitOverrideRepository overrideRepository;
    private final ClinicBookingLimitOverrideChangeLogRepository changeLogRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final ClinicRepository clinicRepository;
    private final ProtectionSettingService protectionSettingService;

    public ClinicBookingLimitOverrideService(
            ClinicBookingLimitOverrideRepository overrideRepository,
            ClinicBookingLimitOverrideChangeLogRepository changeLogRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            ClinicRepository clinicRepository,
            ProtectionSettingService protectionSettingService) {
        this.overrideRepository = overrideRepository;
        this.changeLogRepository = changeLogRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.clinicRepository = clinicRepository;
        this.protectionSettingService = protectionSettingService;
    }

    private void requireClinicAdmin(UUID callerAccountId, UUID clinicId) {
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin)) {
            throw new ClinicProtectionForbiddenException();
        }
    }

    public ClinicBookingLimitOverrideResponse get(UUID callerAccountId, UUID clinicId) {
        requireClinicAdmin(callerAccountId, clinicId);
        Integer current = overrideRepository.findByClinic_Id(clinicId).map(ClinicBookingLimitOverride::getMaxActiveAppointments)
                .orElse(null);
        return new ClinicBookingLimitOverrideResponse(current, protectionSettingService.getGlobalMaxActiveAppointments());
    }

    @Transactional
    public ClinicBookingLimitOverrideResponse update(UUID callerAccountId, UUID clinicId, int maxActiveAppointments, String changedBy) {
        requireClinicAdmin(callerAccountId, clinicId);
        int globalCap = protectionSettingService.getGlobalMaxActiveAppointments();
        if (maxActiveAppointments > globalCap || maxActiveAppointments <= 0) {
            throw new ClinicLimitExceedsGlobalCapException(maxActiveAppointments, globalCap);
        }

        Instant now = Instant.now();
        Optional<ClinicBookingLimitOverride> existing = overrideRepository.findByClinic_Id(clinicId);
        Integer previous = existing.map(ClinicBookingLimitOverride::getMaxActiveAppointments).orElse(null);

        if (existing.isPresent()) {
            existing.get().update(maxActiveAppointments, now, changedBy);
        } else {
            Clinic clinic = clinicRepository.getReferenceById(clinicId);
            overrideRepository.save(new ClinicBookingLimitOverride(clinic, maxActiveAppointments, now, changedBy));
        }
        changeLogRepository.save(new ClinicBookingLimitOverrideChangeLog(
                clinicRepository.getReferenceById(clinicId), previous, maxActiveAppointments, now, changedBy));

        return new ClinicBookingLimitOverrideResponse(maxActiveAppointments, globalCap);
    }

    @Transactional
    public void delete(UUID callerAccountId, UUID clinicId, String changedBy) {
        requireClinicAdmin(callerAccountId, clinicId);
        Optional<ClinicBookingLimitOverride> existing = overrideRepository.findByClinic_Id(clinicId);
        if (existing.isEmpty()) {
            return;
        }
        Integer previous = existing.get().getMaxActiveAppointments();
        overrideRepository.deleteByClinic_Id(clinicId);
        changeLogRepository.save(new ClinicBookingLimitOverrideChangeLog(
                clinicRepository.getReferenceById(clinicId), previous, null, Instant.now(), changedBy));
    }

    public List<ClinicBookingLimitOverrideHistoryEntryResponse> history(UUID callerAccountId, UUID clinicId) {
        requireClinicAdmin(callerAccountId, clinicId);
        return changeLogRepository.findByClinic_IdOrderByChangedAtDesc(clinicId).stream()
                .map(ClinicBookingLimitOverrideHistoryEntryResponse::of)
                .toList();
    }
}
