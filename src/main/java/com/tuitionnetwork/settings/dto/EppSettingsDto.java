package com.tuitionnetwork.settings.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EPP configuration (Settings › EPP Configuration). Consumed by the EPP quote /
 * plan-creation logic once that is wired to read config instead of constants.
 *
 * @param tenors          which tenors (months) are offered, e.g. {3:true, 6:true, 12:true, 18:true}
 * @param interestRatePct flat annual % per tenor, e.g. {3:0, 6:12, 12:14, 18:16}
 */
public record EppSettingsDto(
        Map<Integer, Boolean> tenors,
        long minAmountEGP,
        long maxAmountEGP,
        Map<Integer, Integer> interestRatePct,
        double adminFeeRatePct,
        long adminFeeCapEGP,
        boolean requireApproval,
        int maxPlansPerStudent
) {
    public static EppSettingsDto defaults() {
        Map<Integer, Boolean> tenors = new LinkedHashMap<>();
        tenors.put(3, true);
        tenors.put(6, true);
        tenors.put(12, true);
        tenors.put(18, true);

        Map<Integer, Integer> rates = new LinkedHashMap<>();
        rates.put(3, 10);
        rates.put(6, 12);
        rates.put(12, 14);
        rates.put(18, 16);

        return new EppSettingsDto(tenors, 5_000L, 100_000L, rates, 1.0, 500L, false, 2);
    }
}
