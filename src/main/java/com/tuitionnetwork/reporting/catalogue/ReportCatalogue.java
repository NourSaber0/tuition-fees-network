package com.tuitionnetwork.reporting.catalogue;

import com.tuitionnetwork.reporting.dto.ReportCatalogueEntry;

import java.util.List;
import java.util.Optional;

/**
 * Static registry of the report types the back-office portal offers
 * (mirrors {@code Reports.tsx} {@code reportTypes}). Entries whose upstream
 * data module is not built yet are marked {@code available = false}.
 */
public final class ReportCatalogue {

    private static final List<String> ALL_FORMATS = List.of("PDF", "XLSX", "CSV");

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
            unavailable("reconciliation", "Reconciliation Report",
                    "Matched and exception reconciliation records across the network",
                    "Reconciliation", false, List.of("reconStatus"),
                    "The reconciliation module (Phase 5) is not implemented yet."),
            unavailable("daily-report", "Daily Summary Report",
                    "Auto-generated end-of-day operational summary with all activity",
                    "Daily", true, List.of(),
                    "Composite end-of-day summary — pending upstream reports.")
    );

    private ReportCatalogue() {
    }

    public static List<ReportCatalogueEntry> all() {
        return ENTRIES;
    }

    public static Optional<ReportCatalogueEntry> find(String id) {
        return ENTRIES.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    private static ReportCatalogueEntry available(String id, String title, String description,
                                                  String category, boolean singleDate,
                                                  List<String> contextFilters) {
        return new ReportCatalogueEntry(id, title, description, category, ALL_FORMATS,
                singleDate, contextFilters, true, null);
    }

    private static ReportCatalogueEntry unavailable(String id, String title, String description,
                                                    String category, boolean singleDate,
                                                    List<String> contextFilters, String reason) {
        return new ReportCatalogueEntry(id, title, description, category, ALL_FORMATS,
                singleDate, contextFilters, false, reason);
    }
}
