package com.cms.identity.doctor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorProfileRepository extends JpaRepository<DoctorProfile, UUID> {

    /** 007: the onboarding-time dedup lookup (FR-002) - matches by the mandatory, per-doctor-unique license number. */
    Optional<DoctorProfile> findByLicenseNumber(String licenseNumber);

    /**
     * super-admin-console-redesign: the verification queue's unified search/sort/page query
     * (007's Super Admin worklist, extended) - replaces the old per-status finders, mirrors
     * ClinicRepository.search exactly. {@code searchPattern} is the caller's already-lowercased,
     * already-{@code %}-wrapped LIKE pattern (or null) - same CONCAT-with-null fix as
     * RoleAssignmentRepository.search. {@code reason} filters the Rejected tab by rejection
     * reason; null means no filter.
     *
     * <p>pagination-unification-2026-09-10: paginated - the platform-wide verification queue
     * grows without bound as more doctors onboard across every clinic.
     */
    @Query(
            value = "SELECT dp FROM DoctorProfile dp WHERE "
                    + "((:status = 'PENDING' AND dp.licenseVerified = false AND dp.rejected = false) "
                    + "OR (:status = 'VERIFIED' AND dp.licenseVerified = true) "
                    + "OR (:status = 'REJECTED' AND dp.rejected = true)) "
                    + "AND (:searchPattern IS NULL OR LOWER(dp.account.name) LIKE :searchPattern "
                    + "OR LOWER(dp.account.email) LIKE :searchPattern OR LOWER(dp.specialization) LIKE :searchPattern "
                    + "OR LOWER(dp.licenseNumber) LIKE :searchPattern) "
                    + "AND (:reason IS NULL OR dp.rejectionReason = :reason)",
            countQuery = "SELECT COUNT(dp) FROM DoctorProfile dp WHERE "
                    + "((:status = 'PENDING' AND dp.licenseVerified = false AND dp.rejected = false) "
                    + "OR (:status = 'VERIFIED' AND dp.licenseVerified = true) "
                    + "OR (:status = 'REJECTED' AND dp.rejected = true)) "
                    + "AND (:searchPattern IS NULL OR LOWER(dp.account.name) LIKE :searchPattern "
                    + "OR LOWER(dp.account.email) LIKE :searchPattern OR LOWER(dp.specialization) LIKE :searchPattern "
                    + "OR LOWER(dp.licenseNumber) LIKE :searchPattern) "
                    + "AND (:reason IS NULL OR dp.rejectionReason = :reason)")
    Page<DoctorProfile> search(
            @Param("status") String status,
            @Param("searchPattern") String searchPattern,
            @Param("reason") DoctorProfile.RejectionReason reason,
            Pageable pageable);

    /** super-admin-console-redesign: the permanent-delete gate/manual sweep's candidate set for the auto-purge job. */
    List<DoctorProfile> findByRejectedTrueAndRejectedAtBefore(Instant cutoff);

    /**
     * 007 FR-008: discovery eligibility is the conjunction of licenseVerified, visible, and
     * an active Role Assignment at a verified clinic - a deactivated Role Assignment at an
     * otherwise-verified clinic must not count (spec Clarifications / analyze fix I1). This
     * is the data-layer contribution; the public discovery endpoint itself is 035's.
     */
    @Query("SELECT dp FROM DoctorProfile dp WHERE dp.licenseVerified = true AND dp.visible = true "
            + "AND EXISTS (SELECT 1 FROM RoleAssignment ra WHERE ra.account = dp.account "
            + "AND ra.active = true AND ra.clinic.verified = true)")
    List<DoctorProfile> findDiscoveryEligible();

    /**
     * 041-staff-console-pickers FR-006/research.md R5: every doctor with an active Doctor role
     * at this clinic - deliberately no {@code licenseVerified}/{@code visible} filter (unlike
     * {@link #findDiscoveryEligible()}), since this is an internal staff picker, not the public
     * discovery feature, and schedule definition has no verification precondition.
     *
     * <p>pagination-unification-2026-09-10: paginated - a clinic with a large doctor roster
     * shouldn't force this picker to fetch every doctor in one response.
     *
     * <p>doctors-search-2026-09-10: {@code searchPattern} is the caller's already-lowercased,
     * already-{@code %}-wrapped LIKE pattern (or null) - matches name, staff code, or
     * specialization. Pre-formatted in Java rather than built via {@code CONCAT(...)} in JPQL;
     * a bound parameter used only inside CONCAT fails Postgres' type inference whenever it's
     * null (see RoleAssignmentRepository.search's identical fix for the exact same bug).
     */
    @Query(
            value = "SELECT dp FROM DoctorProfile dp WHERE EXISTS (SELECT 1 FROM RoleAssignment ra "
                    + "WHERE ra.account = dp.account AND ra.clinic.id = :clinicId "
                    + "AND ra.role = com.cms.identity.account.RoleAssignment.Role.Doctor AND ra.active = true) "
                    + "AND (:searchPattern IS NULL OR LOWER(dp.account.name) LIKE :searchPattern "
                    + "OR LOWER(dp.account.staffCode) LIKE :searchPattern OR LOWER(dp.specialization) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(dp) FROM DoctorProfile dp WHERE EXISTS (SELECT 1 FROM RoleAssignment ra "
                    + "WHERE ra.account = dp.account AND ra.clinic.id = :clinicId "
                    + "AND ra.role = com.cms.identity.account.RoleAssignment.Role.Doctor AND ra.active = true) "
                    + "AND (:searchPattern IS NULL OR LOWER(dp.account.name) LIKE :searchPattern "
                    + "OR LOWER(dp.account.staffCode) LIKE :searchPattern OR LOWER(dp.specialization) LIKE :searchPattern)")
    Page<DoctorProfile> findByClinicStaffed(
            @Param("clinicId") UUID clinicId, @Param("searchPattern") String searchPattern, Pageable pageable);

    /** Doctor self-scoping follow-up to 041-staff-console-pickers: resolves the calling Account's own DoctorProfile, if any. */
    Optional<DoctorProfile> findByAccount_Id(UUID accountId);

    /**
     * pagination-unification-2026-09-10: every distinct specialization among this clinic's
     * Doctors (active or inactive Role Assignment - matches the Roster's original client-side
     * derivation, which read from the full, unfiltered, active-and-inactive roster), independent
     * of the current page or the specialization filter itself - mirrors {@code
     * SessionRepository.findDistinctDoctorsInWindow}'s identical stable-filter-options pattern,
     * so the filter's own dropdown never shrinks to just what's on the current page.
     */
    @Query("SELECT DISTINCT dp.specialization FROM DoctorProfile dp WHERE EXISTS (SELECT 1 FROM RoleAssignment ra "
            + "WHERE ra.account = dp.account AND ra.clinic.id = :clinicId "
            + "AND ra.role = com.cms.identity.account.RoleAssignment.Role.Doctor) ORDER BY dp.specialization ASC")
    List<String> findDistinctSpecializationsByClinic(@Param("clinicId") UUID clinicId);

    /** Bulk lookup backing ClinicStaffController's staff-list enrichment (specialization/experience) - avoids one query per row. */
    List<DoctorProfile> findByAccount_IdIn(Collection<UUID> accountIds);
}
