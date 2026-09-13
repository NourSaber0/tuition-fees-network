package com.tuitionnetwork.reporting.catalogue;

import com.tuitionnetwork.reporting.dto.ReportCatalogueEntry;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Static registry of the report types the back-office and school portals offer.
 */
public final class ReportCatalogue {

    private static final List<String> ALL_FORMATS = List.of("PDF", "XLSX", "CSV");
    private static final List<String> SCHOOL_FORMATS = List.of("PDF", "CSV");

    private static final List<ReportCatalogueEntry> ENTRIES = List.of(
            available("network-collections", "Network Collections",
                    "Aggregate collection data across all participating institutions",
                    "Collections", false, List.of("feeType", "paymentStatus")),
            available("collections-by-institution", "Collections by Institution",
                    "Per-institution breakdown with payment method and status trends",
                    "Collections", false, List.of("feeType", "paymentStatus")),
            available("daily-collections", "Daily Collections",
                    "All collections for a selected date, broken down by institution",
                    "Daily", true, List.of()),
            available("payments", "Payments Report",
                    "Full payment register with status, method, and fee type",
                    "Payments", false, List.of("feeType", "paymentStatus", "paymentMethod")),
            available("failed-transactions", "Failed Transactions",
                    "All failed and reversed transactions with reason codes",
                    "Payments", false, List.of("paymentMethod")),
            available("epp-report", "EPP Portfolio Report",
                    "All EPP plans — principal, tenor, repayment status, paid/outstanding",
                    "EPP", false, List.of("eppTenor", "eppStatus")),
            available("outstanding-balances", "Outstanding Balances",
                    "Institutions and students with outstanding fee balances",
                    "Collections", false, List.of("feeType")),
            available("collections-by-type", "Collections by Institution Type",
                    "Schools vs Universities — side-by-side collection comparison",
                    "Collections", false, List.of()),
            available("reconciliation", "Reconciliation Report",
                    "Matched and exception reconciliation records across the network",
                    "Reconciliation", false, List.of("reconStatus")),
            available("daily-report", "Daily Summary Report",
                    "Auto-generated end-of-day operational summary with all activity by fee type",
                    "Daily", true, List.of())
    );

    private static final List<ReportCatalogueEntry> SCHOOL_ENTRIES = List.of(
            availableSchool("school-collections", "Collection Report",
                    "Aggregate collection data and breakdown for your institution",
                    "Collections", false, List.of("feeType", "paymentMethod", "paymentStatus")),
            availableSchool("school-payments", "Payment History",
                    "Full payment register with student details, fee type, and payment method",
                    "Payments", false, List.of("feeType", "paymentStatus", "paymentMethod")),
            availableSchool("school-outstanding-fees", "Outstanding Fees",
                    "Students with outstanding fee balances and upcoming due dates",
                    "Fees", false, List.of("feeType")),
            availableSchool("school-partial-payments", "Partial Payments",
                    "Fee lines with partial instalment payments received",
                    "Fees", false, List.of("feeType"))
    );

    private ReportCatalogue() {
    }

    public static List<ReportCatalogueEntry> all() {
        return ENTRIES;
    }

    public static List<ReportCatalogueEntry> schoolEntries() {
        return SCHOOL_ENTRIES;
    }

    public static boolean isSchoolReport(String id) {
        return SCHOOL_ENTRIES.stream().anyMatch(e -> e.id().equals(id));
    }

    public static Optional<ReportCatalogueEntry> find(String id) {
        return Stream.concat(ENTRIES.stream(), SCHOOL_ENTRIES.stream())
                .filter(e -> e.id().equals(id))
                .findFirst();
    }

    private static ReportCatalogueEntry available(String id, String title, String description,
                                                  String category, boolean singleDate,
                                                  List<String> contextFilters) {
        return new ReportCatalogueEntry(id, title, description, category, ALL_FORMATS,
                singleDate, contextFilters, true, null);
    }

    private static ReportCatalogueEntry availableSchool(String id, String title, String description,
                                                        String category, boolean singleDate,
                                                        List<String> contextFilters) {
        return new ReportCatalogueEntry(id, title, description, category, SCHOOL_FORMATS,
                singleDate, contextFilters, true, null);
    }

    private static ReportCatalogueEntry unavailable(String id, String title, String description,
                                                    String category, boolean singleDate,
                                                    List<String> contextFilters, String reason) {
        return new ReportCatalogueEntry(id, title, description, category, ALL_FORMATS,
                singleDate, contextFilters, false, reason);
    }
}
