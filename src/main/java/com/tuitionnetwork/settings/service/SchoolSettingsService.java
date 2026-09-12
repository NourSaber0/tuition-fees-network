package com.tuitionnetwork.settings.service;

import com.tuitionnetwork.identity.dto.auth.MessageResponse;
import com.tuitionnetwork.settings.dto.ChangePasswordRequest;
import com.tuitionnetwork.settings.dto.SchoolNotificationSettingsDto;
import com.tuitionnetwork.settings.dto.SchoolNotificationSettingsRequest;
import com.tuitionnetwork.settings.dto.SchoolProfileDto;

import java.util.UUID;

public interface SchoolSettingsService {

    SchoolProfileDto getSchoolProfile(UUID schoolId);

    SchoolNotificationSettingsDto getNotificationSettings(UUID schoolId);

    SchoolNotificationSettingsDto updateNotificationSettings(UUID schoolId, SchoolNotificationSettingsRequest request, UUID actorId);

    MessageResponse changePassword(UUID userId, ChangePasswordRequest request);
}
