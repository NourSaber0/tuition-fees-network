package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

public record MoiVerificationResponse(
        @JsonProperty("verification_id") String verificationId,
        @JsonProperty("valid") boolean valid,
        @JsonProperty("national_id") String nationalId,
        @JsonProperty("record_status") String recordStatus,
        @JsonProperty("holder") HolderInfo holder,
        @JsonProperty("name_match") NameMatchInfo nameMatch,
        @JsonProperty("eligibility") EligibilityInfo eligibility,
        @JsonProperty("reasons") List<String> reasons
) {
    public record HolderInfo(
            @JsonProperty("full_name_en") String fullNameEn,
            @JsonProperty("birth_date") LocalDate birthDate,
            @JsonProperty("age") int age,
            @JsonProperty("gender") String gender,
            @JsonProperty("governorate") String governorate,
            @JsonProperty("checksum_valid") boolean checksumValid
    ) {}

    public record NameMatchInfo(
            @JsonProperty("matched") boolean matched,
            @JsonProperty("score") double score
    ) {}

    public record EligibilityInfo(
            @JsonProperty("is_adult") boolean isAdult,
            @JsonProperty("minimum_age") int minimumAge,
            @JsonProperty("can_be_issued_card") boolean canBeIssuedCard
    ) {}
}
