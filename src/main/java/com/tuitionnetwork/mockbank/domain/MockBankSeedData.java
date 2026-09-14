package com.tuitionnetwork.mockbank.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MockBankSeedData {

    public record TestNationalIdRecord(
            String nationalId,
            String fullNameEn,
            LocalDate birthDate,
            String gender,
            String governorate,
            String status, // ACTIVE, BLOCKED, DECEASED, NOT_FOUND
            boolean valid,
            List<String> defaultReasons
    ) {}

    public static final Map<String, TestNationalIdRecord> PRESET_NATIONAL_IDS = Map.of(
            "29805150101023", new TestNationalIdRecord("29805150101023", "Mona Samir Abdelrahman", LocalDate.of(1998, 5, 15), "FEMALE", "Cairo", "ACTIVE", true, List.of()),
            "30103222103442", new TestNationalIdRecord("30103222103442", "Nour Khaled Fahmy", LocalDate.of(2001, 3, 22), "FEMALE", "Giza", "ACTIVE", true, List.of()),
            "29511020204536", new TestNationalIdRecord("29511020204536", "Ahmed Tarek Mahmoud", LocalDate.of(1995, 11, 2), "MALE", "Alexandria", "ACTIVE", true, List.of()),
            "29001301202283", new TestNationalIdRecord("29001301202283", "Salma Hosny Ibrahim", LocalDate.of(1990, 1, 30), "FEMALE", "Dakahlia", "ACTIVE", true, List.of()),
            "30007091301775", new TestNationalIdRecord("30007091301775", "Youssef Adel Nabil", LocalDate.of(2000, 7, 9), "MALE", "Sharqia", "ACTIVE", true, List.of()),
            "28809252506666", new TestNationalIdRecord("28809252506666", "Heba Mostafa Zaki", LocalDate.of(1988, 9, 25), "FEMALE", "Asyut", "ACTIVE", true, List.of()),
            "31204010102041", new TestNationalIdRecord("31204010102041", "Malak Hany Sobhy", LocalDate.of(2012, 4, 1), "FEMALE", "Cairo", "ACTIVE", false, List.of("UNDER_MINIMUM_AGE"))
    );

    public record TestCardRecord(
            String number,
            String scheme,
            String type, // CREDIT or DEBIT
            String outcome, // APPROVED, DECLINED, NEEDS_3DS, ISSUER_UNAVAILABLE
            String responseCode,
            String responseMessage
    ) {}

    public static final List<TestCardRecord> TEST_CARDS = List.of(
            new TestCardRecord("4111 1111 1111 1111", "VISA", "CREDIT", "APPROVED", "00", "Approved (Visa credit)"),
            new TestCardRecord("5555 5555 5555 4444", "MASTERCARD", "CREDIT", "APPROVED", "00", "Approved (Mastercard credit)"),
            new TestCardRecord("5105 1051 0510 5100", "MASTERCARD", "CREDIT", "APPROVED", "00", "Approved (Mastercard credit)"),
            new TestCardRecord("5078 0312 3456 7890", "MEEZA", "CREDIT", "APPROVED", "00", "Approved (Meeza credit)"),
            new TestCardRecord("4000 0566 5566 5556", "VISA", "DEBIT", "APPROVED", "00", "Approved — but debit, so EPP rejects it"),
            new TestCardRecord("4000 0000 0000 0077", "VISA", "CREDIT", "APPROVED_UP_TO_10K", "00", "Approved up to 10,000 EGP, declines above that"),
            new TestCardRecord("4000 0000 0000 0002", "VISA", "CREDIT", "DECLINED", "51", "Declined — insufficient funds (51)"),
            new TestCardRecord("4000 0000 0000 9995", "VISA", "CREDIT", "DECLINED", "05", "Declined — do not honour (05)"),
            new TestCardRecord("4000 0000 0000 0101", "VISA", "CREDIT", "DECLINED", "82", "Declined — incorrect CVV (82)"),
            new TestCardRecord("4000 0000 0000 0069", "VISA", "CREDIT", "DECLINED", "54", "Declined — expired card (54)"),
            new TestCardRecord("4000 0000 0000 0119", "VISA", "CREDIT", "ISSUER_UNAVAILABLE", "91", "HTTP 502, issuer unavailable (retryable)"),
            new TestCardRecord("4000 0000 0000 3220", "VISA", "CREDIT", "NEEDS_3DS", "00", "Needs 3-D Secure — confirm with OTP 123456")
    );

    public static Optional<TestCardRecord> findTestCard(String cardNumber) {
        String clean = CardLib.cleanCardNumber(cardNumber);
        return TEST_CARDS.stream()
                .filter(c -> CardLib.cleanCardNumber(c.number()).equals(clean))
                .findFirst();
    }
}
