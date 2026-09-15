package com.cms.identity.clinic;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicRepository extends JpaRepository<Clinic, UUID> {

    /**
     * super-admin-console-redesign: the verification queue's unified search/sort/page query,
     * replacing the plain per-status finders above for list rendering - stress-test expectation
     * is hundreds to thousands of clinics, so free-text search and sort both need to happen
     * server-side, not by downloading a tab's worth of rows to filter in the browser.
     *
     * <p>{@code searchPattern} is the caller's already-lowercased, already-{@code %}-wrapped LIKE
     * pattern (or null) - matches RoleAssignmentRepository.search's identical fix for the
     * CONCAT-with-null Postgres type-inference bug (a bound parameter used only inside CONCAT
     * fails whenever it's null). {@code reason} filters the Rejected tab by rejection reason;
     * null means no filter, and is ignored entirely for the Pending/Verified statuses.
     */
    @Query(
            value = "SELECT c FROM Clinic c WHERE "
                    + "((:status = 'PENDING' AND c.verified = false AND c.rejected = false) "
                    + "OR (:status = 'VERIFIED' AND c.verified = true) "
                    + "OR (:status = 'REJECTED' AND c.rejected = true)) "
                    + "AND (:searchPattern IS NULL OR LOWER(c.name) LIKE :searchPattern "
                    + "OR LOWER(c.address) LIKE :searchPattern OR LOWER(c.contactEmail) LIKE :searchPattern "
                    + "OR LOWER(c.contactMobile) LIKE :searchPattern) "
                    + "AND (:reason IS NULL OR c.rejectionReason = :reason)",
            countQuery = "SELECT COUNT(c) FROM Clinic c WHERE "
                    + "((:status = 'PENDING' AND c.verified = false AND c.rejected = false) "
                    + "OR (:status = 'VERIFIED' AND c.verified = true) "
                    + "OR (:status = 'REJECTED' AND c.rejected = true)) "
                    + "AND (:searchPattern IS NULL OR LOWER(c.name) LIKE :searchPattern "
                    + "OR LOWER(c.address) LIKE :searchPattern OR LOWER(c.contactEmail) LIKE :searchPattern "
                    + "OR LOWER(c.contactMobile) LIKE :searchPattern) "
                    + "AND (:reason IS NULL OR c.rejectionReason = :reason)")
    Page<Clinic> search(
            @Param("status") String status,
            @Param("searchPattern") String searchPattern,
            @Param("reason") Clinic.RejectionReason reason,
            Pageable pageable);

    /** super-admin-console-redesign: the permanent-delete gate/manual sweep's candidate set for the auto-purge job. */
    List<Clinic> findByRejectedTrueAndRejectedAtBefore(Instant cutoff);
}
