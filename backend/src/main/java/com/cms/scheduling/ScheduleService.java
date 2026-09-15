package com.cms.scheduling;

import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.scheduling.dto.CreateScheduleRequest;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 013: create + list. 014-schedule-overlap-block adds the cross-clinic overlap check in
 * {@link #create}. 016-schedule-edit-non-retroactivity adds {@link #edit} - it, and
 * everything it calls, never references {@link SessionRepository}/{@code Session}
 * anywhere, which is what makes non-retroactivity structural rather than merely tested
 * (research.md). No Session/Slot write of any kind from this class (FR-011 of 013).
 */
@Service
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ClinicRepository clinicRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final EntityManager entityManager;

    public ScheduleService(
            ScheduleRepository scheduleRepository,
            ClinicRepository clinicRepository,
            DoctorProfileRepository doctorProfileRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            EntityManager entityManager) {
        this.scheduleRepository = scheduleRepository;
        this.clinicRepository = clinicRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Schedule create(UUID callerAccountId, UUID clinicId, UUID doctorProfileId, CreateScheduleRequest request) {
        Clinic clinic = clinicRepository.findById(clinicId).orElseThrow(() -> new ClinicNotFoundException(clinicId));
        DoctorProfile doctorProfile = doctorProfileRepository
                .findById(doctorProfileId)
                .orElseThrow(() -> new DoctorProfileNotFoundException(doctorProfileId));

        requireAuthorized(callerAccountId, clinicId, doctorProfile);
        validate(request);

        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                doctorProfile.getAccount().getId(), clinicId, RoleAssignment.Role.Doctor)) {
            throw new DoctorNotStaffedAtClinicException(doctorProfileId, clinicId);
        }

        lockDoctorForOverlapCheck(doctorProfileId);
        requireNoOverlap(doctorProfileId, request, null);

        Schedule schedule = new Schedule(
                doctorProfile,
                clinic,
                request.daysOfWeek(),
                request.startTime(),
                request.endTime(),
                request.mode(),
                request.slotIntervalMinutes());
        return scheduleRepository.save(schedule);
    }

    /**
     * 016: edits an existing Schedule's own days/time-range/mode/slot-interval. Re-runs
     * the same authorization (FR-002), validation (FR-003), and overlap (FR-004, excluding
     * this Schedule from its own comparison set) checks {@link #create} does, all before
     * mutating the loaded entity (FR-005: a rejected edit leaves it untouched). Never
     * references {@code SessionRepository}/{@code Session} (FR-006).
     */
    @Transactional
    public Schedule edit(
            UUID callerAccountId, UUID clinicId, UUID doctorProfileId, UUID scheduleId, CreateScheduleRequest request) {
        clinicRepository.findById(clinicId).orElseThrow(() -> new ClinicNotFoundException(clinicId));
        DoctorProfile doctorProfile = doctorProfileRepository
                .findById(doctorProfileId)
                .orElseThrow(() -> new DoctorProfileNotFoundException(doctorProfileId));
        Schedule schedule = scheduleRepository
                .findById(scheduleId)
                .filter(s -> s.getClinic().getId().equals(clinicId) && s.getDoctorProfile().getId().equals(doctorProfileId))
                .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));

        requireAuthorized(callerAccountId, clinicId, doctorProfile);
        validate(request);
        lockDoctorForOverlapCheck(doctorProfileId);
        requireNoOverlap(doctorProfileId, request, scheduleId);

        schedule.setDaysOfWeek(request.daysOfWeek());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setMode(request.mode());
        schedule.setSlotIntervalMinutes(request.slotIntervalMinutes());
        return scheduleRepository.save(schedule);
    }

    @Transactional(readOnly = true)
    public List<Schedule> list(UUID callerAccountId, UUID clinicId, UUID doctorProfileId) {
        clinicRepository.findById(clinicId).orElseThrow(() -> new ClinicNotFoundException(clinicId));
        DoctorProfile doctorProfile = doctorProfileRepository
                .findById(doctorProfileId)
                .orElseThrow(() -> new DoctorProfileNotFoundException(doctorProfileId));

        requireAuthorized(callerAccountId, clinicId, doctorProfile);

        return scheduleRepository.findByClinic_IdAndDoctorProfile_Id(clinicId, doctorProfileId);
    }

    /** FR-001..FR-003, FR-010: an active ClinicAdmin at this clinic, or the named doctor themselves. */
    private void requireAuthorized(UUID callerAccountId, UUID clinicId, DoctorProfile doctorProfile) {
        boolean isClinicAdmin = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin);
        boolean isDoctorSelf = doctorProfile.getAccount().getId().equals(callerAccountId)
                && roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerAccountId, clinicId, RoleAssignment.Role.Doctor);

        if (!isClinicAdmin && !isDoctorSelf) {
            throw new ForbiddenException();
        }
    }

    /** FR-005..FR-008. */
    private void validate(CreateScheduleRequest request) {
        if (request.daysOfWeek() == null || request.daysOfWeek().isEmpty()) {
            throw new InvalidScheduleException("At least one day of the week is required");
        }
        if (request.startTime() == null || request.endTime() == null || !request.startTime().isBefore(request.endTime())) {
            throw new InvalidScheduleException("startTime must be strictly before endTime");
        }
        if (request.mode() == ScheduleMode.FIXED_TIME) {
            if (request.slotIntervalMinutes() == null || request.slotIntervalMinutes() <= 0) {
                throw new InvalidScheduleException("slotIntervalMinutes must be a positive value in FIXED_TIME mode");
            }
        } else if (request.mode() == ScheduleMode.QUEUE) {
            if (request.slotIntervalMinutes() != null) {
                throw new InvalidScheduleException("slotIntervalMinutes must not be set in QUEUE mode");
            }
        } else {
            throw new InvalidScheduleException("mode is required");
        }
    }

    /**
     * _diagnostics [CRITICAL] - [full-repo-audit] - [SCHEDULE_OVERLAP_RACE]: {@link
     * #requireNoOverlap} is a pure in-Java check-then-act with no DB constraint behind it - a
     * per-schedule-day EXCLUDE constraint isn't practical here since {@code daysOfWeek} lives in
     * a separate {@code schedule_day} join table, not a single-row range column. Two concurrent
     * create/edit calls for the same doctor - including the very first Schedule ever created for
     * that doctor, where {@code findByDoctorProfile_Id} returns zero rows for a row-lock to hold
     * onto - could otherwise both pass the overlap check and both commit, producing overlapping
     * active Schedules the nightly generator then materializes into double-booked Sessions. A
     * Postgres transaction-scoped advisory lock keyed on the doctor closes this: serializes every
     * create/edit for one doctor, auto-releases at commit/rollback (no manual unlock, no leak on
     * exception), and needs no schema change. {@code hashtext} on the UUID's text form gives a
     * stable int4 lock key.
     */
    private void lockDoctorForOverlapCheck(UUID doctorProfileId) {
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(CAST(:doctorProfileId AS text)))")
                .setParameter("doctorProfileId", doctorProfileId.toString())
                .getSingleResult();
    }

    /**
     * 014 FR-001/FR-002: checked against every existing Schedule for this doctor, across
     * every clinic - not scoped to the clinic named in this request. Standard half-open
     * interval overlap (research.md): touching ranges (one ends exactly when the other
     * starts) are never rejected (FR-003). {@code excludeScheduleId} (016 FR-004) skips
     * comparing an edit's own pre-edit state against itself; {@code null} on the create
     * path (nothing to exclude).
     */
    private void requireNoOverlap(UUID doctorProfileId, CreateScheduleRequest request, UUID excludeScheduleId) {
        for (Schedule existing : scheduleRepository.findByDoctorProfile_Id(doctorProfileId)) {
            if (excludeScheduleId != null && existing.getId().equals(excludeScheduleId)) {
                continue;
            }
            boolean sharedDay = !Collections.disjoint(existing.getDaysOfWeek(), request.daysOfWeek());
            boolean timeOverlap = request.startTime().isBefore(existing.getEndTime())
                    && existing.getStartTime().isBefore(request.endTime());
            if (sharedDay && timeOverlap) {
                throw new ScheduleOverlapException(doctorProfileId, existing.getId());
            }
        }
    }
}
