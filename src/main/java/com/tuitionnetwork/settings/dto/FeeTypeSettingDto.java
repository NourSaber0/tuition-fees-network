package com.tuitionnetwork.settings.dto;

import com.tuitionnetwork.settings.domain.FeeTypeSetting;

import java.util.UUID;

public record FeeTypeSettingDto(UUID id, String name, String code, boolean taxable, boolean active) {

    public static FeeTypeSettingDto from(FeeTypeSetting f) {
        return new FeeTypeSettingDto(f.getId(), f.getName(), f.getCode(), f.isTaxable(), f.isActive());
    }
}
