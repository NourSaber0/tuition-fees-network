package com.tuitionnetwork.epp.dto;

public record CardValidationResponse(
        boolean eligible,
        String bank,
        String cardType,
        Integer maxTenor,
        String reason,
        String result
) {
    public static CardValidationResponse eligible(String bank, String cardType, int maxTenor) {
        return new CardValidationResponse(true, bank, cardType, maxTenor, null, "valid-credit");
    }

    public static CardValidationResponse ineligible(String reason) {
        String res = (reason != null && reason.toLowerCase().contains("debit")) ? "rejected-debit" : "rejected-not-eligible";
        return new CardValidationResponse(false, null, null, null, reason, res);
    }

    public CardValidationResponse(String result) {
        this("valid-credit".equalsIgnoreCase(result),
                "valid-credit".equalsIgnoreCase(result) ? "CIB" : null,
                "valid-credit".equalsIgnoreCase(result) ? "Credit" : null,
                "valid-credit".equalsIgnoreCase(result) ? 18 : null,
                "valid-credit".equalsIgnoreCase(result) ? null :
                        ("rejected-debit".equalsIgnoreCase(result) ? "Debit cards not eligible for EPP" : "Card not eligible for EPP"),
                result);
    }
}
