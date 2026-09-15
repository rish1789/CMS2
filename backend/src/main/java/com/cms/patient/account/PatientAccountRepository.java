package com.cms.patient.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientAccountRepository extends JpaRepository<PatientAccount, UUID> {

    boolean existsByEmail(String email);

    Optional<PatientAccount> findByEmail(String email);
}
