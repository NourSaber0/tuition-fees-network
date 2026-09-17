package com.tuitionnetwork.mockbank.domain;

import com.tuitionnetwork.mockbank.dto.CustomerLookupResponse;
import com.tuitionnetwork.mockbank.dto.CustomerLookupResponse.AccountDto;
import com.tuitionnetwork.mockbank.dto.CustomerLookupResponse.CardDto;

import java.math.BigDecimal;
import java.util.List;

public class MockBankCustomers {

    public static List<CustomerLookupResponse> getInitialCustomers() {
        return List.of(
                new CustomerLookupResponse(
                        "cif_100001", "29805150101023", "Mona Samir Abdelrahman", "منى سمير عبدالرحمن", "01001234567", "ACTIVE",
                        List.of(
                                new AccountDto("acc_mona_current", "1001000012345", "CURRENT", "EGP", new BigDecimal("50000"), "ACTIVE"),
                                new AccountDto("acc_mona_savings", "1001000012346", "SAVINGS", "EGP", new BigDecimal("200000"), "ACTIVE")
                        ),
                        List.of(
                                new CardDto("card_mona_visa", "411111******1111", "VISA", "CREDIT", "MONA SAMIR", "12/2030", "ACTIVE", new BigDecimal("100000"), new BigDecimal("100000"), null),
                                new CardDto("card_mona_debit", "507803******7890", "MEEZA", "DEBIT", "MONA SAMIR", "12/2030", "ACTIVE", null, null, "acc_mona_current")
                        )
                ),
                new CustomerLookupResponse(
                        "cif_100002", "30103222103442", "Nour Khaled Fahmy", "نور خالد فهمي", "01001234568", "ACTIVE",
                        List.of(
                                new AccountDto("acc_nour_current", "1001000012347", "CURRENT", "EGP", new BigDecimal("2500"), "ACTIVE")
                        ),
                        List.of(
                                new CardDto("card_nour_mastercard", "555555******4444", "MASTERCARD", "CREDIT", "NOUR KHALED", "12/2030", "ACTIVE", new BigDecimal("10000"), new BigDecimal("10000"), null)
                        )
                ),
                new CustomerLookupResponse(
                        "cif_100003", "29511020204536", "Ahmed Tarek Mahmoud", "أحمد طارق محمود", "01001234569", "ACTIVE",
                        List.of(
                                new AccountDto("acc_ahmed_current", "1001000012348", "CURRENT", "EGP", new BigDecimal("10000"), "FROZEN")
                        ),
                        List.of(
                                new CardDto("card_ahmed_visa", "411111******2222", "VISA", "CREDIT", "AHMED TAREK", "12/2030", "BLOCKED", new BigDecimal("50000"), new BigDecimal("50000"), null),
                                new CardDto("card_ahmed_mastercard", "510510******5100", "MASTERCARD", "CREDIT", "AHMED TAREK", "12/2030", "ACTIVE", new BigDecimal("40000"), new BigDecimal("40000"), null)
                        )
                ),
                new CustomerLookupResponse(
                        "cif_100004", "29001301202283", "Salma Hosny Ibrahim", "سلمى حسني إبراهيم", "01001234570", "ACTIVE",
                        List.of(
                                new AccountDto("acc_salma_savings", "1001000012349", "SAVINGS", "EGP", new BigDecimal("5000"), "DORMANT")
                        ),
                        List.of(
                                new CardDto("card_salma_visa", "411111******3333", "VISA", "CREDIT", "SALMA HOSNY", "12/2020", "EXPIRED", new BigDecimal("20000"), new BigDecimal("20000"), null)
                        )
                ),
                new CustomerLookupResponse(
                        "cif_100005", "30007091301775", "Youssef Adel Nabil", "يوسف عادل نبيل", "01001234571", "ACTIVE",
                        List.of(
                                new AccountDto("acc_youssef_current", "1001000012350", "CURRENT", "EGP", new BigDecimal("20000"), "ACTIVE")
                        ),
                        List.of(
                                new CardDto("card_youssef_debit", "400005******5556", "VISA", "DEBIT", "YOUSSEF ADEL", "12/2030", "ACTIVE", null, null, "acc_youssef_current")
                        )
                ),
                new CustomerLookupResponse(
                        "cif_100006", "28809252506666", "Heba Mostafa Zaki", "هبة مصطفى زكي", "01001234572", "ACTIVE",
                        List.of(),
                        List.of(
                                new CustomerLookupResponse.CardDto("card_heba_visa", "4111********4444", "Visa", "CREDIT", "Heba Ali", "10/30", "ISSUER_UNAVAILABLE", new BigDecimal("100000"), new BigDecimal("100000"), null)
                        )
                )
        );
    }
}
