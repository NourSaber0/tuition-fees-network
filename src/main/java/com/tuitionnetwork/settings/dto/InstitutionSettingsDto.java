package com.tuitionnetwork.settings.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Institution onboarding configuration (Settings › School Configuration). */
public record InstitutionSettingsDto(
        boolean requireDualApproval,
        boolean autoIntegrationAfterApproval,
        @JsonProperty("requireMOECertificate")
        @JsonAlias({"requireMoeCertificate", "requireMOECertificate"})
        boolean requireMoeCertificate,
        int maxStudentsPerUpload,
        int postApprovalActivationDelayHours,
        UploadFormats allowedUploadFormats
) {
    public record UploadFormats(boolean xlsx, boolean csv, boolean xml) {
    }

    public static InstitutionSettingsDto defaults() {
        return new InstitutionSettingsDto(
                true, false, true, 5_000, 24,
                new UploadFormats(true, true, false));
    }
}
