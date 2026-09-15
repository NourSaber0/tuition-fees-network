"use client";

import { useEffect, useState } from "react";
import { useApiClient } from "@tuition/api-client";
import { LoadingSpinner, EmptyState } from "@tuition/ui";

import type {
    ReconciliationSummary,
    ReconciliationTransaction,
    ReconciliationTransactionsResponse,
} from "./types";

export default function ReconciliationPage() {
    const apiClient = useApiClient();

    // =========================
    // STATE
    // =========================

    const [summary, setSummary] =
        useState<ReconciliationSummary | null>(null);

    const [transactions, setTransactions] = useState<
        ReconciliationTransaction[]
    >([]);

    const [loading, setLoading] = useState(true);
    const [transactionsLoading, setTransactionsLoading] =
        useState(true);

    const [error, setError] = useState<string | null>(null);

    // Filters
    const [search, setSearch] = useState("");
    const [status, setStatus] = useState("");
    const [dateFrom, setDateFrom] = useState("");
    const [dateTo, setDateTo] = useState("");

    // Pagination
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);

    // =========================
    // LOAD SUMMARY
    // =========================

    useEffect(() => {
        const loadSummary = async () => {
            try {
                setLoading(true);
                setError(null);

                const response =
                    await apiClient.get<ReconciliationSummary>(
                        "/reconciliation/summary"
                    );

                setSummary(response);
            } catch (err) {
                console.error(
                    "Failed to load reconciliation summary:",
                    err
                );

                setError("Unable to load reconciliation data.");
            } finally {
                setLoading(false);
            }
        };

        loadSummary();
    }, [apiClient]);

    // =========================
    // RESET PAGE WHEN FILTERS CHANGE
    // =========================

    useEffect(() => {
        setPage(0);
    }, [status, dateFrom, dateTo, search]);

    // =========================
    // LOAD TRANSACTIONS
    // =========================

    useEffect(() => {
        const loadTransactions = async () => {
            try {
                setTransactionsLoading(true);

                const params = new URLSearchParams();

                // Backend uses zero-based pages
                params.set("page", page.toString());
                params.set("pageSize", "25");

                // Status
                if (status) {
                    params.set("status", status);
                }

                // Date From
                if (dateFrom) {
                    params.set("dateFrom", dateFrom);
                }

                // Date To
                if (dateTo) {
                    params.set("dateTo", dateTo);
                }

                // Search
                if (search.trim()) {
                    const searchValue = search.trim();

                    if (searchValue.toUpperCase().startsWith("TX")) {
                        params.set("paymentId", searchValue);
                    } else {
                        params.set("studentId", searchValue);
                    }
                }

                const response =
                    await apiClient.get<ReconciliationTransactionsResponse>(
                        `/reconciliation/transactions?${params.toString()}`
                    );

                setTransactions(response.data);
                setTotalPages(response.totalPages);
            } catch (err) {
                console.error(
                    "Failed to load reconciliation transactions:",
                    err
                );

                setTransactions([]);
                setTotalPages(0);
            } finally {
                setTransactionsLoading(false);
            }
        };

        loadTransactions();
    }, [
        apiClient,
        status,
        dateFrom,
        dateTo,
        search,
        page,
    ]);

    // =========================
    // LOADING
    // =========================

    if (loading) {
        return (
            <div className="flex min-h-[400px] items-center justify-center">
                <LoadingSpinner />
            </div>
        );
    }

    // =========================
    // ERROR
    // =========================

    if (error || !summary) {
        return (
            <div className="p-6">
                <EmptyState
                    title="Unable to load reconciliation"
                    description={
                        error ?? "No reconciliation data available."
                    }
                />
            </div>
        );
    }

    // =========================
    // PAGE
    // =========================

    return (
        <div className="space-y-7 p-6">

            {/* =========================
          VIEW-ONLY BANNER
          ========================= */}

            <div className="flex items-start gap-4 rounded-2xl border border-[#D7E6FF] bg-[#F0F6FF] px-7 py-6">

                <div className="mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full border-2 border-[#23458A] text-[#23458A]">
          <span className="text-sm font-semibold">
            i
          </span>
                </div>

                <p className="text-[17px] leading-7 text-[#23458A]">
                    This is a{" "}
                    <strong className="font-semibold">
                        view-only
                    </strong>{" "}
                    reconciliation dashboard. Reconciliation is
                    performed by the bank. School users can monitor
                    reconciliation status for their payments here.
                </p>

            </div>


            {/* =========================
          SUMMARY CARDS
          ========================= */}

            <div className="grid grid-cols-1 gap-6 md:grid-cols-3">

                {/* RECONCILED */}

                <div className="flex h-[140px] items-center gap-6 rounded-2xl border border-[#E5E7EB] bg-white px-8 shadow-sm">

                    <div className="flex h-[60px] w-[60px] items-center justify-center rounded-full bg-[#EDFFF4]">

                        <div className="flex h-7 w-7 items-center justify-center rounded-full border-2 border-[#00A651] text-[#00A651]">

              <span className="text-lg font-bold">
                ✓
              </span>

                        </div>

                    </div>

                    <div>

                        <p className="text-[38px] font-semibold leading-none text-[#00A651]">
                            {summary.totalReconciled}
                        </p>

                        <p className="mt-2 text-[18px] text-[#6B7280]">
                            Reconciled
                        </p>

                    </div>

                </div>


                {/* PENDING */}

                <div className="flex h-[140px] items-center gap-6 rounded-2xl border border-[#E5E7EB] bg-white px-8 shadow-sm">

                    <div className="flex h-[60px] w-[60px] items-center justify-center rounded-full bg-[#FFF8E8]">

                        <div className="flex h-7 w-7 items-center justify-center rounded-full border-2 border-[#E87B00] text-[#E87B00]">

              <span className="text-base font-bold">
                !
              </span>

                        </div>

                    </div>

                    <div>

                        <p className="text-[38px] font-semibold leading-none text-[#E87B00]">
                            {summary.totalPending}
                        </p>

                        <p className="mt-2 text-[18px] text-[#6B7280]">
                            Pending
                        </p>

                    </div>

                </div>


                {/* UNRECONCILED */}

                <div className="flex h-[140px] items-center gap-6 rounded-2xl border border-[#E5E7EB] bg-white px-8 shadow-sm">

                    <div className="flex h-[60px] w-[60px] items-center justify-center rounded-full bg-[#FFF0F0]">

                        <div className="flex h-7 w-7 items-center justify-center rounded-full border-2 border-[#E00000] text-[#E00000]">

              <span className="text-base font-bold">
                ×
              </span>

                        </div>

                    </div>

                    <div>

                        <p className="text-[38px] font-semibold leading-none text-[#E00000]">
                            {summary.totalUnreconciled}
                        </p>

                        <p className="mt-2 text-[18px] text-[#6B7280]">
                            Unreconciled
                        </p>

                    </div>

                </div>

            </div>


            {/* =========================
          FILTERS
          ========================= */}

            <div className="flex flex-wrap items-center gap-4">

                {/* SEARCH */}

                <div className="relative min-w-[280px] flex-1">

                    <input
                        type="text"
                        value={search}
                        onChange={(e) => {
                            setSearch(e.target.value);
                        }}
                        placeholder="Search by reference or student..."
                        className="h-14 w-full rounded-xl border border-[#DDE3EC] bg-white px-5 text-[17px] text-[#26344D] outline-none placeholder:text-[#9AA5B7] focus:border-[#23458A]"
                    />

                </div>


                {/* STATUS */}

                <select
                    value={status}
                    onChange={(e) => {
                        setStatus(e.target.value);
                    }}
                    className="h-14 min-w-[200px] rounded-xl border border-[#DDE3EC] bg-white px-5 text-[17px] text-[#26344D] outline-none focus:border-[#23458A]"
                >

                    <option value="">
                        All Statuses
                    </option>

                    <option value="Reconciled">
                        Reconciled
                    </option>

                    <option value="Pending">
                        Pending
                    </option>

                    <option value="Unreconciled">
                        Unreconciled
                    </option>

                </select>


                {/* DATE FROM */}

                <input
                    type="date"
                    value={dateFrom}
                    onChange={(e) => {
                        setDateFrom(e.target.value);
                    }}
                    className="h-14 rounded-xl border border-[#DDE3EC] bg-white px-5 text-[17px] text-[#4B5563] outline-none focus:border-[#23458A]"
                />


                <span className="text-[17px] text-[#9AA5B7]">
          to
        </span>


                {/* DATE TO */}

                <input
                    type="date"
                    value={dateTo}
                    onChange={(e) => {
                        setDateTo(e.target.value);
                    }}
                    className="h-14 rounded-xl border border-[#DDE3EC] bg-white px-5 text-[17px] text-[#4B5563] outline-none focus:border-[#23458A]"
                />

            </div>


            {/* =========================
          TRANSACTIONS TABLE
          ========================= */}

            <div className="overflow-hidden rounded-2xl border border-[#E5E7EB] bg-white shadow-sm">

                <div className="overflow-x-auto">

                    <table className="w-full min-w-[900px]">

                        {/* HEADER */}

                        <thead className="bg-[#F8FAFD]">

                        <tr>

                            <th className="px-7 py-5 text-left text-sm font-semibold uppercase tracking-wide text-[#6B7280]">
                                Reference
                            </th>

                            <th className="px-7 py-5 text-left text-sm font-semibold uppercase tracking-wide text-[#6B7280]">
                                Student
                            </th>

                            <th className="px-7 py-5 text-left text-sm font-semibold uppercase tracking-wide text-[#6B7280]">
                                Fee
                            </th>

                            <th className="px-7 py-5 text-left text-sm font-semibold uppercase tracking-wide text-[#6B7280]">
                                Amount
                            </th>

                            <th className="px-7 py-5 text-left text-sm font-semibold uppercase tracking-wide text-[#6B7280]">
                                Date
                            </th>

                            <th className="px-7 py-5 text-left text-sm font-semibold uppercase tracking-wide text-[#6B7280]">
                                Payment Status
                            </th>

                        </tr>

                        </thead>


                        {/* BODY */}

                        <tbody>

                        {transactionsLoading ? (

                            <tr>

                                <td
                                    colSpan={6}
                                    className="px-7 py-12 text-center text-[#6B7280]"
                                >
                                    Loading transactions...
                                </td>

                            </tr>

                        ) : transactions.length === 0 ? (

                            <tr>

                                <td
                                    colSpan={6}
                                    className="px-7 py-12 text-center text-[#6B7280]"
                                >
                                    No reconciliation transactions found.
                                </td>

                            </tr>

                        ) : (

                            transactions.map((transaction) => (

                                <tr
                                    key={transaction.paymentId}
                                    className="border-t border-[#EEF1F5]"
                                >

                                    {/* REFERENCE */}

                                    <td className="px-7 py-6 text-[16px] font-medium text-[#23458A]">
                                        {transaction.paymentId}
                                    </td>


                                    {/* STUDENT */}

                                    <td className="px-7 py-6 text-[16px] text-[#26344D]">
                                        {transaction.studentId}
                                    </td>


                                    {/* FEE */}

                                    <td className="px-7 py-6 text-[16px] text-[#6B7280]">
                                        {transaction.feeId}
                                    </td>


                                    {/* AMOUNT */}

                                    <td className="px-7 py-6 text-[16px] font-semibold text-[#172033]">
                                        {transaction.amountEGP.toLocaleString()} EGP
                                    </td>


                                    {/* DATE */}

                                    <td className="px-7 py-6 text-[16px] text-[#6B7280]">
                                        {transaction.date}
                                    </td>


                                    {/* STATUS */}

                                    <td className="px-7 py-6">

                      <span
                          className={`inline-flex rounded-full border px-4 py-1.5 text-sm font-medium ${
                              transaction.reconciliationStatus ===
                              "Reconciled"
                                  ? "border-[#A7F3C5] bg-[#F0FFF6] text-[#008A43]"
                                  : transaction.reconciliationStatus ===
                                  "Pending"
                                      ? "border-[#FFD580] bg-[#FFF9E8] text-[#D97706]"
                                      : "border-[#FFB4B4] bg-[#FFF2F2] text-[#DC2626]"
                          }`}
                      >
                        {transaction.reconciliationStatus}
                      </span>

                                    </td>

                                </tr>

                            ))

                        )}

                        </tbody>

                    </table>

                </div>


                {/* =========================
            PAGINATION
            ========================= */}

                {totalPages > 1 && (

                    <div className="flex items-center justify-between border-t border-[#EEF1F5] px-7 py-4">

                        <button
                            type="button"
                            disabled={page === 0}
                            onClick={() =>
                                setPage((current) => current - 1)
                            }
                            className="rounded-lg border border-[#DDE3EC] px-4 py-2 text-sm text-[#23458A] disabled:cursor-not-allowed disabled:opacity-40"
                        >
                            Previous
                        </button>


                        <span className="text-sm text-[#6B7280]">
              Page {page + 1} of {totalPages}
            </span>


                        <button
                            type="button"
                            disabled={page >= totalPages - 1}
                            onClick={() =>
                                setPage((current) => current + 1)
                            }
                            className="rounded-lg border border-[#DDE3EC] px-4 py-2 text-sm text-[#23458A] disabled:cursor-not-allowed disabled:opacity-40"
                        >
                            Next
                        </button>

                    </div>

                )}

            </div>

        </div>
    );
}