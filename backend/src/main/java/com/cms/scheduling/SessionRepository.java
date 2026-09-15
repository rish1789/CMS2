package com.cms.scheduling;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    /** 015 research.md: the pre-check driving which candidate dates still need generating. */
    List<Session> findBySchedule_IdAndSessionDateIn(UUID scheduleId, List<LocalDate> sessionDates);

    List<Session> findBySchedule_Id(UUID scheduleId);

    /** super-admin-console-redesign: the clinic permanent-delete gate - a generated Session counts as real activity. */
    long countByClinic_Id(UUID clinicId);

    /** super-admin-console-redesign: the doctor permanent-delete gate - a generated Session counts as real activity. */
    long countByDoctorProfile_Id(UUID doctorProfileId);

    /**
     * 042-day-sheet-hardening FR-004/005/006: the day sheet's session list, paginated, with two
     * independent optional doctor restrictions ANDed together - {@code selfScopeDoctorProfileId}
     * (the caller's own doctor id, non-null only when they're Doctor-self-scoped) and
     * {@code requestedDoctorProfileId} (the explicit filter param). Keeping them as two separate
     * conditions - rather than one Java-side "pick whichever applies" - means a Doctor-only
     * caller who explicitly filters by a *different* doctor's id correctly gets zero rows
     * (both conditions must hold), not that other doctor's sessions.
     */
    @Query("SELECT s FROM Session s WHERE s.clinic.id = :clinicId AND s.sessionDate BETWEEN :from AND :to "
            + "AND (:selfScopeDoctorProfileId IS NULL OR s.doctorProfile.id = :selfScopeDoctorProfileId) "
            + "AND (:requestedDoctorProfileId IS NULL OR s.doctorProfile.id = :requestedDoctorProfileId) "
            + "ORDER BY s.sessionDate ASC")
    Page<Session> findByClinicAndWindow(
            @Param("clinicId") UUID clinicId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("selfScopeDoctorProfileId") UUID selfScopeDoctorProfileId,
            @Param("requestedDoctorProfileId") UUID requestedDoctorProfileId,
            Pageable pageable);

    /**
     * 042-day-sheet-hardening FR-004/data-model.md: every doctor with >=1 session in the window,
     * independent of the current page or the explicit doctorProfileId filter (so the filter's
     * own option list never changes as the caller pages through results) - but still subject to
     * the same self-scoping restriction as the main list, so a Doctor-only caller never sees
     * another doctor's name in their own filter dropdown.
     *
     * <p>staffCode included alongside name so the frontend can search by either - mirrors the
     * Roster page's own "search by name or staff code" precedent (Account.staffCode is the
     * human-facing "Staff ID" used throughout this app, e.g. "DR-6789" - not the raw UUID).
     */
    @Query("SELECT DISTINCT s.doctorProfile.id AS doctorProfileId, s.doctorProfile.account.name AS name, "
            + "s.doctorProfile.account.staffCode AS staffCode "
            + "FROM Session s WHERE s.clinic.id = :clinicId AND s.sessionDate BETWEEN :from AND :to "
            + "AND (:selfScopeDoctorProfileId IS NULL OR s.doctorProfile.id = :selfScopeDoctorProfileId)")
    List<DoctorInWindow> findDistinctDoctorsInWindow(
            @Param("clinicId") UUID clinicId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("selfScopeDoctorProfileId") UUID selfScopeDoctorProfileId);

    interface DoctorInWindow {
        UUID getDoctorProfileId();

        String getName();

        String getStaffCode();
    }

    /**
     * patient-booking-flow-rebuild: every today-or-later Queue-mode Session at a clinic - the
     * patient-facing browse list replacing the raw "type a Session ID" field on the queue-booking
     * entry flow. {@code doctorProfileId} is optional, mirroring {@code SlotRepository
     * .findOpenFixedTimeSlots}'s own optional-doctor-filter shape, so a Discovery search result
     * for one specific doctor can land here pre-filtered. Session-level {@code startTime} (unlike
     * a Queue-mode {@code Slot}'s own startTime) is never null - set at generation time for every
     * Session regardless of mode - so ordering by it is always safe.
     */
    @Query("SELECT s FROM Session s WHERE s.clinic.id = :clinicId AND s.mode = com.cms.scheduling.ScheduleMode.QUEUE "
            + "AND s.sessionDate >= :from "
            + "AND (:doctorProfileId IS NULL OR s.doctorProfile.id = :doctorProfileId) "
            + "ORDER BY s.sessionDate ASC, s.startTime ASC")
    Page<Session> findUpcomingQueueSessionsByClinic(
            @Param("clinicId") UUID clinicId,
            @Param("doctorProfileId") UUID doctorProfileId,
            @Param("from") LocalDate from,
            Pageable pageable);
}
