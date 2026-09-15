package com.cms.identity.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByEmail(String email);

    boolean existsByStaffCode(String staffCode);

    /** 004-staff-onboarding-direct-hire: used by staff login to resolve the caller's Account. */
    Optional<Account> findByEmail(String email);

    /** 006-staff-login-dual-identifier: the alternate lookup key for staff login. */
    Optional<Account> findByStaffCode(String staffCode);
}
