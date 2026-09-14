package com.tuitionnetwork.mockbank.domain;

import java.time.YearMonth;
import java.util.regex.Pattern;

public class CardLib {

    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d{12,19}$");

    public static String cleanCardNumber(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[\\s-]", "");
    }

    public static boolean isValidLuhn(String cardNumber) {
        String clean = cleanCardNumber(cardNumber);
        if (!DIGITS_ONLY.matcher(clean).matches()) {
            return false;
        }

        int sum = 0;
        boolean alternate = false;
        for (int i = clean.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(clean.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    public static String detectScheme(String cardNumber) {
        String clean = cleanCardNumber(cardNumber);
        if (clean.startsWith("5078")) {
            return "MEEZA";
        }
        if (clean.startsWith("4")) {
            return "VISA";
        }
        if (clean.matches("^(5[1-5]|2[2-7]).*")) {
            return "MASTERCARD";
        }
        if (clean.matches("^(34|37).*")) {
            return "AMEX";
        }
        return null;
    }

    public static String detectCardType(String cardNumber) {
        String clean = cleanCardNumber(cardNumber);
        // Test debit card explicitly defined in Mock Banking Services spec
        if (clean.startsWith("40000566") || clean.equals("4000056655665556")) {
            return "DEBIT";
        }
        return "CREDIT";
    }

    public static boolean isExpired(int expiryMonth, int expiryYear) {
        if (expiryMonth < 1 || expiryMonth > 12) {
            return true;
        }
        YearMonth currentYearMonth = YearMonth.now();
        YearMonth cardYearMonth = YearMonth.of(expiryYear, expiryMonth);
        return cardYearMonth.isBefore(currentYearMonth);
    }

    public static String maskCardNumber(String cardNumber) {
        String clean = cleanCardNumber(cardNumber);
        if (clean.length() < 10) return clean;
        return clean.substring(0, 6) + "******" + clean.substring(clean.length() - 4);
    }
}
