package com.tuitionnetwork.payments.domain;

public final class CardBinClassifier {

    private CardBinClassifier() {
    }

    public static boolean isDebitCard(String cardNumber) {
        if (cardNumber == null) return false;
        String clean = cardNumber.replaceAll("\\s+", "").toUpperCase();
        return clean.contains("DEBIT") ||
               clean.startsWith("5078") ||
               clean.startsWith("5888") ||
               clean.startsWith("6703") ||
               clean.startsWith("400005") ||
               clean.startsWith("400000") ||
               clean.startsWith("4023") ||
               clean.startsWith("5000");
    }
}
