package com.cms.protection.repository;

import com.cms.protection.domain.ProtectionSettingChangeLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProtectionSettingChangeLogRepository extends JpaRepository<ProtectionSettingChangeLog, UUID> {

    List<ProtectionSettingChangeLog> findBySettingNameOrderByChangedAtDesc(String settingName);
}
