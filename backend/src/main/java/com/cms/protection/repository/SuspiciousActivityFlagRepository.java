package com.cms.protection.repository;

import com.cms.protection.domain.SuspiciousActivityFlag;
import com.cms.protection.domain.SuspiciousActivityFlagStatus;
import com.cms.protection.domain.SuspiciousActivitySignalType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuspiciousActivityFlagRepository extends JpaRepository<SuspiciousActivityFlag, UUID> {

    /** Real patient activity at a clinic - blocks permanently deleting a rejected clinic. */
    long countByClinic_Id(UUID clinicId);

    /** FR-014/Clarifications dedup check: is there already an OUTSTANDING flag for this exact (patient, clinic, signal)? */
    Optional<SuspiciousActivityFlag> findByPatientAccount_IdAndClinic_IdAndSignalTypeAndStatus(
            UUID patientAccountId, UUID clinicId, SuspiciousActivitySignalType signalType, SuspiciousActivityFlagStatus status);

    /** FR-021/FR-022: this clinic's own flags only - a flag with a null clinicId (the global-limit fact) is never a stored row, so this never needs to special-case it. */
    Page<SuspiciousActivityFlag> findByClinic_Id(UUID clinicId, Pageable pageable);

    Page<SuspiciousActivityFlag> findByClinic_IdAndStatus(UUID clinicId, SuspiciousActivityFlagStatus status, Pageable pageable);

    Page<SuspiciousActivityFlag> findByClinic_IdAndPatientAccount_Id(UUID clinicId, UUID patientAccountId, Pageable pageable);

    Page<SuspiciousActivityFlag> findByClinic_IdAndStatusAndPatientAccount_Id(
            UUID clinicId, SuspiciousActivityFlagStatus status, UUID patientAccountId, Pageable pageable);

    /** FR-022/BR-004: a flag detail lookup scoped to the requesting clinic - a mismatch is a 404, not a 403 (this codebase's established cross-tenant-lookup convention). */
    Optional<SuspiciousActivityFlag> findByIdAndClinic_Id(UUID id, UUID clinicId);
}
