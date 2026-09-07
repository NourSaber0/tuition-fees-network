package com.tuitionnetwork.epp.dto;

import java.math.BigDecimal;

public record EppQuoteRequest(
        BigDecimal principalEGP,
        Integer tenor
) {
}
