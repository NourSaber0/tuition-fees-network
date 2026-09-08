package com.tuitionnetwork.settings.service;

import com.tuitionnetwork.settings.dto.CreateFeeTypeRequest;
import com.tuitionnetwork.settings.dto.EppSettingsDto;
import com.tuitionnetwork.settings.dto.FeeTypeSettingDto;
import com.tuitionnetwork.settings.dto.InstitutionSettingsDto;
import com.tuitionnetwork.settings.dto.NotificationSettingsDto;
import com.tuitionnetwork.settings.dto.PaymentStatusInfo;
import com.tuitionnetwork.settings.dto.UpdateFeeTypeRequest;

import java.util.List;
import java.util.UUID;

/**
 * System settings (Phase 11). Config blobs ({@code epp}, {@code notifications},
 * {@code institutions}) are stored as JSON in {@code system_setting}; fee types
 * are their own table. Defaults are seeded lazily on first read. Every mutation
 * writes an audit entry.
 */
public interface SettingsService {

    List<FeeTypeSettingDto> getFeeTypes();

    FeeTypeSettingDto createFeeType(CreateFeeTypeRequest request);

    FeeTypeSettingDto updateFeeType(UUID id, UpdateFeeTypeRequest request);

    List<PaymentStatusInfo> getPaymentStatuses();

    EppSettingsDto getEpp();

    EppSettingsDto updateEpp(EppSettingsDto settings);

    NotificationSettingsDto getNotifications();

    NotificationSettingsDto updateNotifications(NotificationSettingsDto settings);

    InstitutionSettingsDto getInstitutionSettings();

    InstitutionSettingsDto updateInstitutionSettings(InstitutionSettingsDto settings);
}
