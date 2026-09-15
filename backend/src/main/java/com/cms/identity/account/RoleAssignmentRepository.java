package com.cms.identity.account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, UUID> {

    /** 004-staff-onboarding-direct-hire: is this Account an active ClinicAdmin for this specific clinic? */
    boolean existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(UUID accountId, UUID clinicId, RoleAssignment.Role role);

    /** 027-queue-position-tracking: is this Account active in ANY role at this specific clinic - the first staff-gated read with no role exclusion at all. */
    boolean existsByAccount_IdAndClinic_IdAndActiveTrue(UUID accountId, UUID clinicId);

    /** 005-last-active-clinicadmin-protection: the Role Assignment (if any) for this Account at this clinic - the deactivation target. */
    Optional<RoleAssignment> findByAccount_IdAndClinic_Id(UUID accountId, UUID clinicId);

    /** 005-last-active-clinicadmin-protection: how many active ClinicAdmins does this clinic have right now? (research.md) */
    long countByClinic_IdAndRoleAndActiveTrue(UUID clinicId, RoleAssignment.Role role);

    /** 017-fee-resolution-locking: every active clinic assignment this Account holds for a given role - used to find every clinic a Doctor is actively staffed at. */
    List<RoleAssignment> findByAccount_IdAndRoleAndActiveTrue(UUID accountId, RoleAssignment.Role role);

    /** 038-unified-realtime-inbox research.md R6: is this Account an active Operations OR ClinicAdmin at this clinic - the front-desk staff gate every Inbox action uses. */
    boolean existsByAccount_IdAndClinic_IdAndRoleInAndActiveTrue(
            UUID accountId, UUID clinicId, Collection<RoleAssignment.Role> roles);

    /**
     * 041-staff-console-pickers FR-001: every clinic this Account currently has an active role
     * at, regardless of which role.
     *
     * <p>pagination-unification-2026-09-10: paginated - an Account with roles at many clinics
     * (a multi-clinic chain's regional staff) shouldn't force a single unbounded fetch.
     */
    Page<RoleAssignment> findByAccount_IdAndActiveTrue(UUID accountId, Pageable pageable);

    /** 041-staff-console-pickers FR-006a: every active staff member (any role) at this clinic - the "Deactivate staff" picker's list. */
    List<RoleAssignment> findByClinic_IdAndActiveTrue(UUID clinicId);

    /**
     * pagination-unification-2026-09-10: the Roster's server-side search/filter/sort/page query,
     * replacing ClinicStaffController's old "fetch every RoleAssignment at the clinic, then
     * filter/sort/paginate in memory" behavior - a UI that already *looked* server-paginated
     * (Page X of Y, Jump to page) but silently downloaded the whole roster underneath.
     *
     * <p>specialization/experienceYears live on DoctorProfile, which has no relationship mapped
     * back from Account/RoleAssignment (DoctorProfile.account is a unidirectional @OneToOne) -
     * joined in explicitly by matching account, LEFT JOIN since a ClinicAdmin/Operations row has
     * none. Each filter is optional (null = don't filter on it), ANDed together.
     *
     * <p>{@code searchPattern} is the caller's already-lowercased, already-{@code %}-wrapped LIKE
     * pattern (or null), not a raw search term - a bound parameter used only inside {@code
     * CONCAT(...)} fails Postgres' type inference whenever it's null ("could not determine data
     * type of parameter"), since CONCAT gives the planner nothing else to infer the parameter's
     * type from. Pre-formatting in Java and using the parameter directly in {@code LIKE} (whose
     * left-hand side already fixes the expected type) sidesteps that entirely.
     */
    @Query(
            value = "SELECT ra FROM RoleAssignment ra LEFT JOIN DoctorProfile dp ON dp.account = ra.account "
                    + "WHERE ra.clinic.id = :clinicId "
                    + "AND (:role IS NULL OR ra.role = :role) "
                    + "AND (:active IS NULL OR ra.active = :active) "
                    + "AND (:specialization IS NULL OR dp.specialization = :specialization) "
                    + "AND (:searchPattern IS NULL OR LOWER(ra.account.name) LIKE :searchPattern "
                    + "OR LOWER(ra.account.staffCode) LIKE :searchPattern)",
            countQuery = "SELECT COUNT(ra) FROM RoleAssignment ra LEFT JOIN DoctorProfile dp ON dp.account = ra.account "
                    + "WHERE ra.clinic.id = :clinicId "
                    + "AND (:role IS NULL OR ra.role = :role) "
                    + "AND (:active IS NULL OR ra.active = :active) "
                    + "AND (:specialization IS NULL OR dp.specialization = :specialization) "
                    + "AND (:searchPattern IS NULL OR LOWER(ra.account.name) LIKE :searchPattern "
                    + "OR LOWER(ra.account.staffCode) LIKE :searchPattern)")
    Page<RoleAssignment> search(
            @Param("clinicId") UUID clinicId,
            @Param("role") RoleAssignment.Role role,
            @Param("active") Boolean active,
            @Param("specialization") String specialization,
            @Param("searchPattern") String searchPattern,
            Pageable pageable);

    /**
     * Doctor self-scoping follow-up to 041-staff-console-pickers: every active role this
     * Account holds at this specific clinic (usually one row, but not enforced as at-most-one
     * anywhere in the schema) - used to tell whether the caller's *only* active role here is
     * Doctor (self-scope their session/slot view) versus also holding ClinicAdmin/Operations
     * (see everything, unchanged).
     */
    List<RoleAssignment> findByAccount_IdAndClinic_IdAndActiveTrue(UUID accountId, UUID clinicId);

    /**
     * super-admin-console-redesign: permanent-delete's cascade cleanup for a clinic that passed
     * its activity gate - every clinic (including a never-verified, rejected one) already has a
     * real ClinicAdmin RoleAssignment created at registration time (ClinicRegistrationService),
     * so this row is always expected to exist and must be cleared before the Clinic itself can
     * be deleted (clinic_id is NOT NULL here).
     */
    long deleteByClinic_Id(UUID clinicId);
}
