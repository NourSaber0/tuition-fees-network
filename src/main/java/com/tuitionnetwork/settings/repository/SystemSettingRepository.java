package com.tuitionnetwork.settings.repository;

import com.tuitionnetwork.settings.domain.SystemSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemSettingRepository extends JpaRepository<SystemSetting, UUID> {
    Optional<SystemSetting> findBySettingKey(String settingKey);
}
