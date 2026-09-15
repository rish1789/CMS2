package com.cms.discovery;

import com.cms.identity.account.RoleAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscoveryResultRepository extends JpaRepository<RoleAssignment, UUID> {

    /**
     * 035 FR-002/FR-003: the entire eligibility conjunction (clinic verified, doctor
     * license verified, doctor visible, active Doctor-role Role Assignment at that
     * specific clinic) plus every optional filter, evaluated together as a single
     * data-layer query - never as a response-stage filter over an unfiltered fetch
     * (Constitution Principle IV).
     *
     * <p>patient-search-advanced-filtering: extends 035's single free-text {@code q} with
     * three independent, optional, ANDed filters - {@code city} (exact, case-insensitive -
     * the hard "results are scoped to the selected city" constraint, enforced whenever a
     * city is actually provided; a null city applies no scoping at all, matching every
     * existing caller that never sent one), {@code specialization} (exact, case-insensitive),
     * and {@code minExperienceYears} (threshold). {@code searchPattern}, {@code city}, and
     * {@code specialization} must already be lowercased by the caller (and {@code
     * searchPattern} {@code %}-wrapped) - never lowercased/CONCAT'd inside JPQL on a nullable
     * parameter. That's not just the established convention elsewhere ({@code
     * DoctorProfileRepository}/{@code ClinicRepository}) - {@code LOWER(c.city) = LOWER(:city)}
     * was tried first here and failed live against the real dev Postgres instance with
     * {@code ERROR: function lower(bytea) does not exist}: with a null {@code :city}, Postgres
     * can't infer the parameter's type from a bare {@code LOWER(?)} call, and silently guesses
     * {@code bytea}. Comparing the already-lowercased Java value directly against {@code
     * LOWER(column)} - never wrapping the parameter itself in {@code LOWER()} - avoids the
     * ambiguity entirely (the column side gives Postgres a concrete type to infer against).
     * Sort is supplied via {@code Sort} rather than hand-written per-field JPQL - Spring Data
     * appends {@code ORDER BY} using the exact alias paths ({@code a.name}, {@code c.name},
     * {@code dp.experienceYears}, {@code dp.specialization}) given by {@link
     * DiscoverySearchService#resolveSort}, verified live against the real dev Postgres instance
     * (unlike the admin queues' own more conservative same-entity-only sort, this cross-join
     * case was actually exercised, not left unverified).
     */
    @Query(
            """
            SELECT new com.cms.discovery.DiscoveryResult(
                dp.id, a.name, dp.specialization, dp.experienceYears, c.id, c.name, c.address, c.city)
            FROM RoleAssignment ra
            JOIN ra.account a
            JOIN ra.clinic c
            JOIN DoctorProfile dp ON dp.account = a
            WHERE ra.active = true
              AND ra.role = com.cms.identity.account.RoleAssignment.Role.Doctor
              AND c.verified = true
              AND dp.licenseVerified = true
              AND dp.visible = true
              AND (:searchPattern IS NULL
                   OR LOWER(dp.specialization) LIKE :searchPattern
                   OR LOWER(a.name) LIKE :searchPattern
                   OR LOWER(c.name) LIKE :searchPattern
                   OR LOWER(c.address) LIKE :searchPattern)
              AND (:city IS NULL OR LOWER(c.city) = :city)
              AND (:specialization IS NULL OR LOWER(dp.specialization) = :specialization)
              AND (:minExperienceYears IS NULL OR dp.experienceYears >= :minExperienceYears)
            """)
    List<DiscoveryResult> search(
            @Param("searchPattern") String searchPattern,
            @Param("city") String city,
            @Param("specialization") String specialization,
            @Param("minExperienceYears") Integer minExperienceYears,
            Sort sort);

    /** patient-search-advanced-filtering: drives the City filter dropdown - only cities that actually have at least one eligible doctor right now, so picking one never dead-ends into an empty result. */
    @Query(
            """
            SELECT DISTINCT c.city
            FROM RoleAssignment ra
            JOIN ra.account a
            JOIN ra.clinic c
            JOIN DoctorProfile dp ON dp.account = a
            WHERE ra.active = true
              AND ra.role = com.cms.identity.account.RoleAssignment.Role.Doctor
              AND c.verified = true
              AND dp.licenseVerified = true
              AND dp.visible = true
              AND c.city IS NOT NULL
            ORDER BY c.city
            """)
    List<String> findDistinctEligibleCities();

    /** patient-search-advanced-filtering: drives the Specialization filter dropdown - same eligibility gate as {@link #findDistinctEligibleCities}. */
    @Query(
            """
            SELECT DISTINCT dp.specialization
            FROM RoleAssignment ra
            JOIN ra.account a
            JOIN ra.clinic c
            JOIN DoctorProfile dp ON dp.account = a
            WHERE ra.active = true
              AND ra.role = com.cms.identity.account.RoleAssignment.Role.Doctor
              AND c.verified = true
              AND dp.licenseVerified = true
              AND dp.visible = true
            ORDER BY dp.specialization
            """)
    List<String> findDistinctEligibleSpecializations();
}
