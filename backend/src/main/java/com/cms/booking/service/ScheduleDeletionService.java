package com.cms.booking.service;

import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.scheduling.domain.Schedule;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.exception.ClinicNotFoundException;
import com.cms.scheduling.exception.DoctorProfileNotFoundException;
import com.cms.scheduling.exception.ForbiddenException;
import com.cms.scheduling.exception.ScheduleNotFoundException;
import com.cms.scheduling.repository.ScheduleRepository;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.repository.SlotRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 055-schedule-break-window: lets a doctor's two side-by-side Schedules (e.g. a morning block
 * and an afternoon block either side of a lunch gap) be merged into one Schedule with a break
 * window - the redundant Schedule can only be removed by first clearing out every Session that
 * still references it (a hard FK), which a stale/never-used Session can simply have deleted
 * outright. A Session with real Booking/waitlist history never can (the exact same "block, don't
 * cascade" rule {@link SessionDeletionService#deleteSession} enforces one Session at a time) -
 * here that Session is detached from the Schedule instead of the whole deletion being rejected,
 * since it already snapshots everything it needs (Session's own Javadoc) and nothing in this
 * codebase ever reads {@code Session.schedule} back.
 *
 * <p>Deletes/detaches every Session directly, via {@link SessionDeletionService#hasRealActivity}
 * plus this class's own repositories, rather than calling {@code SessionDeletionService
 * .deleteSession} and catching its exception - found live testing the schedule-merge flow that
 * doesn't work: with the default REQUIRED propagation, that method's own {@code @Transactional}
 * shares this method's physical transaction, and Spring's AOP proxy marks it rollback-only the
 * instant the exception is thrown regardless of the caller catching it, so the whole
 * {@code deleteSchedule} transaction then fails to commit with {@code UnexpectedRollbackException}.
 * Doing every check-then-act step in this one transaction keeps the whole operation atomic - the
 * Schedule and every Session it owns change together, or none of them do.
 */
@Service
public class ScheduleDeletionService {

    private final ScheduleRepository scheduleRepository;
    private final ClinicRepository clinicRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final SessionRepository sessionRepository;
    private final SlotRepository slotRepository;
    private final SessionDeletionService sessionDeletionService;

    public ScheduleDeletionService(
            ScheduleRepository scheduleRepository,
            ClinicRepository clinicRepository,
            DoctorProfileRepository doctorProfileRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            SessionRepository sessionRepository,
            SlotRepository slotRepository,
            SessionDeletionService sessionDeletionService) {
        this.scheduleRepository = scheduleRepository;
        this.clinicRepository = clinicRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
        this.sessionDeletionService = sessionDeletionService;
    }

    @Transactional
    public void deleteSchedule(UUID callerAccountId, UUID clinicId, UUID doctorProfileId, UUID scheduleId) {
        clinicRepository.findById(clinicId).orElseThrow(() -> new ClinicNotFoundException(clinicId));
        DoctorProfile doctorProfile = doctorProfileRepository
                .findById(doctorProfileId)
                .orElseThrow(() -> new DoctorProfileNotFoundException(doctorProfileId));
        Schedule schedule = scheduleRepository
                .findById(scheduleId)
                .filter(s -> s.getClinic().getId().equals(clinicId) && s.getDoctorProfile().getId().equals(doctorProfileId))
                .orElseThrow(() -> new ScheduleNotFoundException(scheduleId));

        requireAuthorized(callerAccountId, clinicId, doctorProfile);

        for (Session session : sessionRepository.findBySchedule_Id(schedule.getId())) {
            if (sessionDeletionService.hasRealActivity(session)) {
                session.detachSchedule();
            } else {
                slotRepository.deleteAll(slotRepository.findBySession_Id(session.getId()));
                sessionRepository.delete(session);
            }
        }
        scheduleRepository.delete(schedule);
    }

    /** Mirrors ScheduleService.requireAuthorized exactly - an active ClinicAdmin at this clinic, or the named doctor themselves. */
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
}
