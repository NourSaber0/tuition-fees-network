package com.tuitionnetwork.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Generic key/value store for singleton configuration blobs
 * ({@code epp}, {@code notifications}, {@code institutions}). The value is
 * a JSON document owned by the {@code settings} service.
 */
@Entity
@Table(name = "system_setting")
public class SystemSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "setting_key", nullable = false, unique = true, length = 64)
    private String settingKey;

    @Lob
    @Column(name = "setting_value", nullable = false)
    private String settingValue;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);

    public SystemSetting() {
    }

    public SystemSetting(String settingKey, String settingValue) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public void setSettingKey(String settingKey) {
        this.settingKey = settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public void setSettingValue(String settingValue) {
        this.settingValue = settingValue;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SystemSetting that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
