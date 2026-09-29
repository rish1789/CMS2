package com.cms.protection.repository;

import com.cms.protection.domain.ProtectionSetting;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProtectionSettingRepository extends JpaRepository<ProtectionSetting, UUID> {

    Optional<ProtectionSetting> findByName(String name);
}
