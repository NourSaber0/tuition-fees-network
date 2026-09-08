package com.tuitionnetwork.identity.dto;

import java.time.LocalDate;

public record InstitutionSettlementDto(
        String id,
        LocalDate date,
        long grossEGP,
        long cibFeeEGP,
        long netEGP,
        String status,
        String txRef,
        String reconRef
) {}
