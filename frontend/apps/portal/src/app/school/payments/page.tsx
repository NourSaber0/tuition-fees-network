"use client";

// ---------------------------------------------------------------------------
// SP-P6 – School Portal Payments (view-only)
// Endpoints: GET /api/v1/payments  |  GET /api/v1/payments/{id}  |  GET /api/v1/payments/export
//
// IMPORTANT: There is NO "process payment" button anywhere in this page.
// Payment processing is a bank-only action by design.
//
// DESIGN NOTE: Matches the Figma "School Portal Design" spec. Two backend
// additions are assumed and called out inline where used:
//   1. `method` query param on GET /payments and /payments/export
//   2. `statusCounts` on the list response (counts across the full filtered
//      result set, not just the current page) — see the `summary` memo
//      below for the client-side fallback used until that field exists.
// ---------------------------------------------------------------------------

import { useCallback, useEffect, useMemo, useState } from "react";

import { useApiClient } from "@tuition/api-client";
import {
  Badge,
  Button,
  EmptyState,
  LoadingSpinner,
  Modal,
  Pagination,
  Table,
} from "@tuition/ui";
import type { Column } from "@tuition/ui";

import type {
  PaymentFilters,
  SchoolPaymentDetail,
  SchoolPaymentItem,
  SchoolPaymentsResponse,
} from "./types";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const PAGE_SIZE = 20;

const STATUS_OPTIONS = [
  { value: "", label: "All Statuses" },
  { value: "Successful", label: "Successful" },
  { value: "Pending", label: "Pending" },
  { value: "Failed", label: "Failed" },
  { value: "Refunded", label: "Refunded" },
  { value: "Reversed", label: "Reversed" },
];

const METHOD_OPTIONS = [
  { value: "", label: "All Methods" },
  { value: "Card", label: "Card" },
  { value: "Bank Transfer", label: "Bank Transfer" },
  { value: "Cash", label: "Cash" },
  { value: "Mobile Wallet", label: "Mobile Wallet" },
];

const DEFAULT_FILTERS: PaymentFilters = {
  search: "",
  status: "",
  method: "",
  dateFrom: "",
  dateTo: "",
  studentId: "",
};

// ---------------------------------------------------------------------------
// Badge helpers
// ---------------------------------------------------------------------------

type BadgeTone = "success" | "warning" | "danger" | "info" | "neutral";

function paymentStatusTone(status: string): BadgeTone {
  switch (status) {
    case "Successful":
      return "success";
    case "Pending":
      return "warning";
    case "Failed":
      return "danger";
    case "Refunded":
    case "Reversed":
      return "info";
    default:
      return "neutral";
  }
}

function reconciliationTone(rec: string): BadgeTone {
  return rec === "Reconciled" ? "success" : "warning";
}

function feeStatusTone(feeStatus: string): BadgeTone {
  switch (feeStatus) {
    case "Paid":
      return "success";
    case "Partial":
      return "warning";
    case "Overdue":
      return "danger";
    case "Active":
      return "info";
    default:
      return "neutral";
  }
}

// Priority badges (P1 = highest) all share one calm, informational tone —
// the number itself carries the urgency, the color doesn't need to shout.
function priorityTone(_priority: number): BadgeTone {
  return "info";
}

// ---------------------------------------------------------------------------
// Money formatter
// ---------------------------------------------------------------------------

function formatEGP(amount: number): string {
  return `EGP ${Math.round(amount).toLocaleString()}`;
}

// ---------------------------------------------------------------------------
// Build query-string for list / export endpoints
// ---------------------------------------------------------------------------

function buildQuery(
  filters: PaymentFilters,
  page: number, // 0-indexed
  pageSize: number
): string {
  const params = new URLSearchParams();
  if (filters.search) params.set("search", filters.search);
  if (filters.status) params.set("status", filters.status);
  if (filters.method) params.set("method", filters.method);
  if (filters.dateFrom) params.set("dateFrom", filters.dateFrom);
  if (filters.dateTo) params.set("dateTo", filters.dateTo);
  if (filters.studentId) params.set("studentId", filters.studentId);
  params.set("page", String(page));
  params.set("pageSize", String(pageSize));
  return params.toString();
}

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

export default function SchoolPaymentsPage() {
  const client = useApiClient();

  // List state
  const [payments, setPayments] = useState<SchoolPaymentItem[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [statusCounts, setStatusCounts] = useState<{
    successful: number;
    pending: number;
    failed: number;
  } | null>(null);
  const [page, setPage] = useState(0); // 0-indexed, backend convention

  // Loading / error
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Filters — status/method/date apply immediately; search commits on
  // Enter or blur so we're not refetching on every keystroke.
  const [filters, setFilters] = useState<PaymentFilters>(DEFAULT_FILTERS);
  const [searchDraft, setSearchDraft] = useState("");

  // Export
  const [exporting, setExporting] = useState(false);

  // Detail modal
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<SchoolPaymentDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  // -------------------------------------------------------------------------
  // Fetch payments list
  //
  // NOTE: `client` (useApiClient) already has `/api/v1` baked into its base
  // URL, so paths passed to client.get() must be relative to that — do NOT
  // prefix them with `/api/v1` again, or the backend receives
  // `/api/v1/api/v1/payments` and 404s with "No static resource ...".
  // -------------------------------------------------------------------------

  const fetchPayments = useCallback(
    async (appliedFilters: PaymentFilters, appliedPage: number) => {
      setLoading(true);
      setError(null);
      let ignore = false;

      try {
        const qs = buildQuery(appliedFilters, appliedPage, PAGE_SIZE);
        const resp = await client.get<SchoolPaymentsResponse>(
          `/payments?${qs}`
        );
        if (!ignore) {
          setPayments(resp.data);
          setTotal(resp.total);
          setTotalPages(resp.totalPages);
          // Backend-provided counts, once available, take priority over
          // the client-side fallback computed in `summary` below.
          setStatusCounts(resp.statusCounts ?? null);
        }
      } catch (err) {
        if (!ignore) {
          setError(
            err instanceof Error ? err.message : "Failed to load payments."
          );
        }
      } finally {
        if (!ignore) setLoading(false);
      }

      return () => {
        ignore = true;
      };
    },
    [client]
  );

  // Re-fetch whenever committed filters or page changes
  useEffect(() => {
    fetchPayments(filters, page);
  }, [filters, page, fetchPayments]);

  // -------------------------------------------------------------------------
  // Fetch payment detail
  // -------------------------------------------------------------------------

  useEffect(() => {
    if (!selectedId) {
      setDetail(null);
      return;
    }

    let ignore = false;
    setDetailLoading(true);
    setDetailError(null);

    client
      .get<SchoolPaymentDetail>(`/payments/${selectedId}`)
      .then((d) => {
        if (!ignore) setDetail(d);
      })
      .catch((err) => {
        if (!ignore)
          setDetailError(
            err instanceof Error ? err.message : "Failed to load payment detail."
          );
      })
      .finally(() => {
        if (!ignore) setDetailLoading(false);
      });

    return () => {
      ignore = true;
    };
  }, [selectedId, client]);

  // -------------------------------------------------------------------------
  // Export CSV
  //
  // NOTE: This one bypasses `client` and calls the browser `fetch()`
  // directly against the app's own route, so it correctly keeps the
  // explicit `/api/v1` prefix — do not remove it here.
  // -------------------------------------------------------------------------

  const handleExport = useCallback(async () => {
    setExporting(true);
    try {
      const qs = buildQuery(filters, 0, 100_000); // no pagination for export
      const rawSession = localStorage.getItem("tuition.auth.session");
      const accessToken = rawSession
        ? (JSON.parse(rawSession) as { accessToken: string }).accessToken
        : "";

      const resp = await fetch(`/api/v1/payments/export?${qs}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });

      if (!resp.ok) throw new Error("Export failed");

      const blob = await resp.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      const today = new Date().toISOString().slice(0, 10);
      a.href = url;
      a.download = `payments-export-${today}.csv`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error("Export error:", err);
    } finally {
      setExporting(false);
    }
  }, [filters]);

  // -------------------------------------------------------------------------
  // Filter handlers
  // -------------------------------------------------------------------------

  function applyImmediateFilter<K extends keyof PaymentFilters>(
    key: K,
    value: PaymentFilters[K]
  ) {
    setFilters((prev) => ({ ...prev, [key]: value }));
    setPage(0);
  }

  function commitSearch() {
    if (searchDraft === filters.search) return;
    setFilters((prev) => ({ ...prev, search: searchDraft }));
    setPage(0);
  }

  function handleSearchKeyDown(e: React.KeyboardEvent) {
    if (e.key === "Enter") commitSearch();
  }

  // Clicking a status pill toggles that status filter on/off.
  function toggleStatusFilter(status: string) {
    applyImmediateFilter("status", filters.status === status ? "" : status);
  }

  // -------------------------------------------------------------------------
  // Status summary — prefer backend-computed counts (whole filtered result
  // set); fall back to counting the current page only, which undercounts
  // once there's more than one page. Swap this out once the API returns
  // `statusCounts` on the list response.
  // -------------------------------------------------------------------------

  const summary = useMemo(() => {
    if (statusCounts) {
      return {
        total,
        successful: statusCounts.successful,
        pending: statusCounts.pending,
        failed: statusCounts.failed,
      };
    }
    return {
      total,
      successful: payments.filter((p) => p.status === "Successful").length,
      pending: payments.filter((p) => p.status === "Pending").length,
      failed: payments.filter((p) => p.status === "Failed").length,
    };
  }, [statusCounts, total, payments]);

  // -------------------------------------------------------------------------
  // Table columns
  // -------------------------------------------------------------------------

  const columns: Column<SchoolPaymentItem>[] = [
    {
      key: "id",
      header: "Reference",
      render: (row) => (
        <span className="font-mono text-xs text-blue-700 break-words">
          {row.id}
        </span>
      ),
    },
    {
      key: "studentName",
      header: "Student",
      render: (row) => (
        <div>
          <p className="font-medium text-gray-900 text-sm">{row.studentName}</p>
          <p className="text-xs text-gray-500">{row.studentId}</p>
        </div>
      ),
    },
    {
      key: "feeName",
      header: "Fee",
      render: (row) => (
        <div>
          <p className="text-sm text-gray-800">{row.feeName}</p>
          {row.isPartial && (
            <span className="inline-block mt-0.5 px-1.5 py-0.5 rounded text-[10px] font-medium bg-amber-50 text-amber-700 border border-amber-200">
              Partial
            </span>
          )}
        </div>
      ),
    },
    {
      key: "feePriority",
      header: "Priority",
      render: (row) => (
        <Badge tone={priorityTone(row.feePriority)}>P{row.feePriority}</Badge>
      ),
    },
    {
      key: "feeDueDate",
      header: "Fee Due Date",
      render: (row) => (
        <span className="text-sm text-gray-700">{row.feeDueDate}</span>
      ),
    },
    {
      key: "amountEGP",
      header: "Amount",
      render: (row) => (
        <span className="font-semibold text-gray-900">
          {formatEGP(row.amountEGP)}
        </span>
      ),
    },
    {
      key: "method",
      header: "Method",
      render: (row) => (
        <span className="text-sm text-gray-700">{row.method}</span>
      ),
    },
    {
      key: "date",
      header: "Date",
      render: (row) => (
        <div className="text-sm text-gray-700">
          <p>{row.date}</p>
          <p className="text-xs text-gray-500">{row.time}</p>
        </div>
      ),
    },
    {
      key: "status",
      header: "Status",
      render: (row) => (
        <Badge tone={paymentStatusTone(row.status)}>{row.status}</Badge>
      ),
    },
    {
      key: "reconciliation",
      header: "Reconciliation",
      render: (row) => (
        <Badge tone={reconciliationTone(row.reconciliation)}>
          {row.reconciliation}
        </Badge>
      ),
    },
  ];

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------

  return (
    <div className="space-y-4">
      {/* ------------------------------------------------------------------ */}
      {/* Page header                                                          */}
      {/* ------------------------------------------------------------------ */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-9 w-9 rounded-lg bg-orange-500 flex items-center justify-center flex-shrink-0">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              className="h-5 w-5 text-white"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={2}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M9 14l6-6m-5.5-.5h.01m5 5h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          </div>
          <h1 className="text-2xl font-bold text-gray-900">Payments</h1>
        </div>

        {/* Export — the ONLY action button in this view */}
        <Button
          variant="secondary"
          size="sm"
          onClick={handleExport}
          disabled={exporting}
          className="flex items-center gap-2"
        >
          {exporting ? (
            <LoadingSpinner size={16} />
          ) : (
            <svg
              xmlns="http://www.w3.org/2000/svg"
              className="h-4 w-4"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={2}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2M7 10l5 5m0 0l5-5m-5 5V4"
              />
            </svg>
          )}
          {exporting ? "Exporting…" : "Export CSV"}
        </Button>
      </div>

      {/* ------------------------------------------------------------------ */}
      {/* Search bar                                                           */}
      {/* ------------------------------------------------------------------ */}
      <div className="relative">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400 pointer-events-none"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M21 21l-4.35-4.35M17 11A6 6 0 1 1 5 11a6 6 0 0 1 12 0z"
          />
        </svg>
        <input
          type="text"
          placeholder="Search by reference, student, or fee…"
          value={searchDraft}
          onChange={(e) => setSearchDraft(e.target.value)}
          onKeyDown={handleSearchKeyDown}
          onBlur={commitSearch}
          className="w-full pl-9 pr-3 py-2.5 text-sm bg-white border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
      </div>

      {/* ------------------------------------------------------------------ */}
      {/* Filter row — status / method / date range, applied immediately      */}
      {/* ------------------------------------------------------------------ */}
      <div className="flex flex-wrap gap-3">
        <select
          value={filters.status}
          onChange={(e) => applyImmediateFilter("status", e.target.value)}
          className="px-3 py-2 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
        >
          {STATUS_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>

        <select
          value={filters.method}
          onChange={(e) => applyImmediateFilter("method", e.target.value)}
          className="px-3 py-2 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
        >
          {METHOD_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>

        <input
          type="date"
          value={filters.dateFrom}
          onChange={(e) => applyImmediateFilter("dateFrom", e.target.value)}
          className="px-3 py-2 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
        />
        <span className="self-center text-sm text-gray-400">to</span>
        <input
          type="date"
          value={filters.dateTo}
          onChange={(e) => applyImmediateFilter("dateTo", e.target.value)}
          className="px-3 py-2 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
        />
      </div>

      {/* ------------------------------------------------------------------ */}
      {/* Status summary pills                                                 */}
      {/* ------------------------------------------------------------------ */}
      {!loading && !error && (
        <div className="flex flex-wrap items-center gap-3">
          <StatPill
            label="Total"
            value={summary.total}
            active={filters.status === ""}
            onClick={() => toggleStatusFilter("")}
          />
          <StatPill
            label="Successful"
            value={summary.successful}
            dotClassName="bg-green-500"
            active={filters.status === "Successful"}
            onClick={() => toggleStatusFilter("Successful")}
          />
          <StatPill
            label="Pending"
            value={summary.pending}
            dotClassName="bg-amber-500"
            active={filters.status === "Pending"}
            onClick={() => toggleStatusFilter("Pending")}
          />
          <StatPill
            label="Failed"
            value={summary.failed}
            dotClassName="bg-red-500"
            active={filters.status === "Failed"}
            onClick={() => toggleStatusFilter("Failed")}
          />

          <p className="ml-auto text-sm text-gray-500">
            {total} result{total !== 1 ? "s" : ""}
          </p>
        </div>
      )}

      {/* ------------------------------------------------------------------ */}
      {/* Error banner                                                         */}
      {/* ------------------------------------------------------------------ */}
      {error && (
        <div className="bg-red-50 border border-red-200 rounded-xl p-4 flex items-center gap-3">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            className="h-5 w-5 text-red-500 flex-shrink-0"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"
            />
          </svg>
          <p className="text-sm text-red-700">{error}</p>
          <Button
            variant="secondary"
            size="sm"
            className="ml-auto"
            onClick={() => fetchPayments(filters, page)}
          >
            Retry
          </Button>
        </div>
      )}

      {/* ------------------------------------------------------------------ */}
      {/* Payments table                                                       */}
      {/* ------------------------------------------------------------------ */}
      {loading ? (
        <div className="flex justify-center py-16">
          <LoadingSpinner size={32} />
        </div>
      ) : !error && payments.length === 0 ? (
        <EmptyState
          title="No payments found"
          description="Try adjusting your filters or date range."
        />
      ) : !error ? (
        <>
          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
            <Table<SchoolPaymentItem>
              columns={columns}
              rows={payments}
              rowKey={(row) => row.id}
              onRowClick={(row) => setSelectedId(row.id)}
            />
          </div>

          {/* Pagination — Pagination component is 1-indexed for display */}
          {totalPages > 1 && (
            <div className="flex justify-center">
              <Pagination
                page={page + 1} // convert 0-indexed → 1-indexed for display
                totalPages={totalPages}
                onPageChange={(p) => setPage(p - 1)} // convert back to 0-indexed
              />
            </div>
          )}
        </>
      ) : null}

      {/* ------------------------------------------------------------------ */}
      {/* Payment Detail Modal                                                 */}
      {/* ------------------------------------------------------------------ */}
      <Modal
        open={!!selectedId}
        onClose={() => {
          setSelectedId(null);
          setDetail(null);
          setDetailError(null);
        }}
        title="Payment Detail"
        footer={
          <Button
            variant="secondary"
            onClick={() => {
              setSelectedId(null);
              setDetail(null);
              setDetailError(null);
            }}
          >
            Close
          </Button>
        }
      >
        {detailLoading && (
          <div className="flex justify-center py-10">
            <LoadingSpinner size={32} />
          </div>
        )}

        {detailError && (
          <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-sm text-red-700">
            {detailError}
          </div>
        )}

        {!detailLoading && !detailError && detail && (
          <div className="space-y-6">
            {/* Header info grid */}
            <div className="grid grid-cols-2 gap-4">
              <InfoRow label="Reference">
                <span className="font-mono text-xs">{detail.id}</span>
              </InfoRow>
              <InfoRow label="Student">
                <span className="font-medium">{detail.studentName}</span>
                <span className="text-xs text-gray-500 ml-1">
                  ({detail.studentId})
                </span>
              </InfoRow>
              <InfoRow label="Amount">
                <span className="font-semibold">
                  {formatEGP(detail.amountEGP)}{" "}
                  <span className="text-xs text-gray-500">
                    ({detail.currency})
                  </span>
                </span>
              </InfoRow>
              <InfoRow label="Method">{detail.method}</InfoRow>
              <InfoRow label="Date &amp; Time">
                {detail.date} {detail.time}
              </InfoRow>
              <InfoRow label="Status">
                <Badge tone={paymentStatusTone(detail.status)}>
                  {detail.status}
                </Badge>
              </InfoRow>
              <InfoRow label="Reconciliation">
                <Badge tone={reconciliationTone(detail.reconciliation)}>
                  {detail.reconciliation}
                </Badge>
              </InfoRow>
            </div>

            {/* Allocation breakdown — only when multi-fee split is present */}
            {detail.allocation && detail.allocation.length > 0 && (
              <div>
                <h3 className="text-sm font-semibold text-gray-800 mb-3">
                  Fee Allocation Breakdown
                  <span className="ml-2 text-xs font-normal text-gray-500">
                    ({detail.allocation.length} fee
                    {detail.allocation.length !== 1 ? "s" : ""})
                  </span>
                </h3>

                <div className="overflow-x-auto rounded-lg border border-gray-200">
                  <table className="min-w-full divide-y divide-gray-100 text-sm">
                    <thead className="bg-gray-50">
                      <tr>
                        {[
                          "Fee",
                          "Category",
                          "Due Date",
                          "Original",
                          "Prev. Paid",
                          "Outstanding",
                          "Allocated",
                          "Remaining",
                          "Fee Status",
                        ].map((h) => (
                          <th
                            key={h}
                            className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase tracking-wide whitespace-nowrap"
                          >
                            {h}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100 bg-white">
                      {detail.allocation.map((alloc) => (
                        <tr
                          key={alloc.feeId}
                          className="hover:bg-gray-50 transition-colors"
                        >
                          <td className="px-3 py-2 font-medium text-gray-900 whitespace-nowrap">
                            {alloc.feeName}
                            {alloc.isOverdue && (
                              <span className="ml-1.5 inline-block px-1.5 py-0.5 rounded text-[10px] font-medium bg-red-50 text-red-700 border border-red-200">
                                Overdue
                              </span>
                            )}
                          </td>
                          <td className="px-3 py-2 text-gray-600 whitespace-nowrap">
                            {alloc.feeCategory}
                          </td>
                          <td className="px-3 py-2 text-gray-600 whitespace-nowrap">
                            {alloc.dueDate}
                          </td>
                          <td className="px-3 py-2 text-gray-700 whitespace-nowrap">
                            {formatEGP(alloc.originalAmountEGP)}
                          </td>
                          <td className="px-3 py-2 text-gray-700 whitespace-nowrap">
                            {formatEGP(alloc.previouslyPaidEGP)}
                          </td>
                          <td className="px-3 py-2 text-gray-700 whitespace-nowrap">
                            {formatEGP(alloc.outstandingEGP)}
                          </td>
                          <td className="px-3 py-2 font-semibold text-gray-900 whitespace-nowrap">
                            {formatEGP(alloc.allocatedEGP)}
                          </td>
                          <td className="px-3 py-2 text-gray-700 whitespace-nowrap">
                            {formatEGP(alloc.remainingAfterEGP)}
                          </td>
                          <td className="px-3 py-2 whitespace-nowrap">
                            <Badge tone={feeStatusTone(alloc.feeStatus)}>
                              {alloc.feeStatus}
                            </Badge>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Helper component – status summary pill (Total / Successful / Pending / Failed)
// ---------------------------------------------------------------------------

function StatPill({
  label,
  value,
  dotClassName,
  active,
  onClick,
}: {
  label: string;
  value: number;
  dotClassName?: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-2 px-4 py-2 rounded-xl border text-sm transition-colors ${
        active
          ? "border-blue-400 bg-blue-50"
          : "border-gray-200 bg-white hover:bg-gray-50"
      }`}
    >
      {dotClassName && (
        <span className={`h-2 w-2 rounded-full ${dotClassName}`} />
      )}
      <span className="font-semibold text-gray-900">{value}</span>
      <span className="text-gray-500">{label}</span>
    </button>
  );
}

// ---------------------------------------------------------------------------
// Helper component – labelled value row in the detail header grid
// ---------------------------------------------------------------------------

function InfoRow({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-0.5">
      <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">
        {label}
      </p>
      <div className="text-sm text-gray-800">{children}</div>
    </div>
  );
}