package com.tuitionnetwork.mockbank.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tuitionnetwork.mockbank.domain.MockBankSeedData;
import java.util.List;
import java.util.Map;

public record TestDataResponse(
        @JsonProperty("cards") List<MockBankSeedData.TestCardRecord> cards,
        @JsonProperty("national_ids") Map<String, MockBankSeedData.TestNationalIdRecord> nationalIds
) {}
