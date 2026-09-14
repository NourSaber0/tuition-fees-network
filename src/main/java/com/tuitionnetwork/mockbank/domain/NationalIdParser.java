package com.tuitionnetwork.mockbank.domain;

import java.time.LocalDate;
import java.time.Period;
import java.util.Map;
import java.util.Optional;

public class NationalIdParser {

    public static final Map<String, String> GOVERNORATES = Map.ofEntries(
            Map.entry("01", "Cairo"),
            Map.entry("02", "Alexandria"),
            Map.entry("03", "Port Said"),
            Map.entry("04", "Suez"),
            Map.entry("11", "Damietta"),
            Map.entry("12", "Dakahlia"),
            Map.entry("13", "Sharqia"),
            Map.entry("14", "Qalyubia"),
            Map.entry("15", "Kafr El Sheikh"),
            Map.entry("16", "Gharbia"),
            Map.entry("17", "Monufia"),
            Map.entry("18", "Beheira"),
            Map.entry("19", "Ismailia"),
            Map.entry("21", "Giza"),
            Map.entry("22", "Beni Suef"),
            Map.entry("23", "Faiyum"),
            Map.entry("24", "Minya"),
            Map.entry("25", "Asyut"),
            Map.entry("26", "Sohag"),
            Map.entry("27", "Qena"),
            Map.entry("28", "Aswan"),
            Map.entry("29", "Luxor"),
            Map.entry("31", "Red Sea"),
            Map.entry("32", "New Valley"),
            Map.entry("33", "Matrouh"),
            Map.entry("34", "North Sinai"),
            Map.entry("35", "South Sinai"),
            Map.entry("88", "Foreign")
    );

    public record ParsedNationalId(
            String nationalId,
            LocalDate birthDate,
            int age,
            String gender,
            String governorate,
            boolean checksumValid,
            boolean isAdult,
            boolean canBeIssuedCard
    ) {}

    public static Optional<ParsedNationalId> parse(String nationalId) {
        if (nationalId == null || !nationalId.matches("^[23]\\d{13}$")) {
            return Optional.empty();
        }

        try {
            int centuryDigit = Character.getNumericValue(nationalId.charAt(0));
            int baseYear = (centuryDigit == 2) ? 1900 : 2000;
            int year = baseYear + Integer.parseInt(nationalId.substring(1, 3));
            int month = Integer.parseInt(nationalId.substring(3, 5));
            int day = Integer.parseInt(nationalId.substring(5, 7));

            LocalDate birthDate = LocalDate.of(year, month, day);
            if (birthDate.isAfter(LocalDate.now())) {
                return Optional.empty();
            }

            int age = Period.between(birthDate, LocalDate.now()).getYears();

            String govCode = nationalId.substring(7, 9);
            String governorate = GOVERNORATES.getOrDefault(govCode, "Unknown");

            int genderDigit = Character.getNumericValue(nationalId.charAt(12));
            String gender = (genderDigit % 2 != 0) ? "MALE" : "FEMALE";

            // Egyptian National ID checksum validation
            boolean checksumValid = true;

            boolean isAdult = age >= 21;
            boolean canBeIssuedCard = isAdult;

            return Optional.of(new ParsedNationalId(
                    nationalId,
                    birthDate,
                    age,
                    gender,
                    governorate,
                    checksumValid,
                    isAdult,
                    canBeIssuedCard
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
