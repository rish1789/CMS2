package com.cms.patient.account.repository;

import com.cms.patient.account.domain.PatientAccount;


import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PatientAccountRepository extends JpaRepository<PatientAccount, UUID> {

    boolean existsByEmail(String email);

    Optional<PatientAccount> findByEmail(String email);

    /**
     * 060-booking-abuse-prevention (research.md Decision 1): the concurrency guard for
     * BookingProtectionService.checkBookingLimit - a row lock on the patient's own account for
     * the duration of the count-then-insert transaction, serializing two simultaneous booking
     * attempts from the same patient so the second always sees the first's freshly-inserted
     * Booking when it re-reads the active-appointment count.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PatientAccount> findWithLockById(UUID id);
}
