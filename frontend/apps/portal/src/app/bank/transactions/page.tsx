"use client";

import { useEffect, useState, useCallback } from "react";
import { useApiClient, useAuth, type PageResponse } from "@tuition/api-client";
import {
  Button,
  Badge,
  LoadingSpinner,
  EmptyState,
  Pagination,
  Modal,
  SearchIcon,
  DownloadIcon,
  PlusIcon,
  RefreshIcon,
  CheckCircleIcon,
  CreditCardIcon,
  BankIcon,
  formatIsoDate,
  dueDateLabel,
  PRIORITY_BADGE_CLASSES,
} from "@tuition/ui";
import type {
  TransactionDto,
  TransactionDetailDto,
  TransactionTabCountsDto,
  CustomerFeesResponse,
  BackOfficePaymentRequest,
  BackOfficePaymentResponse,
} from "./types";

type StatusTab = "ALL" | "SUCCESSFUL" | "PENDING" | "FAILED";

const STATUS_PILL_STYLES: Record<string, { bg: string; text: string; border: string }> = {
  Successful: { bg: "bg-emerald-50", text: "text-emerald-700", border: "border-emerald-200" },
  Pending: { bg: "bg-amber-50", text: "text-amber-700", border: "border-amber-200" },
  Failed: { bg: "bg-rose-50", text: "text-rose-700", border: "border-rose-200" },
};

function formatMoney(amount: number | undefined): string {
  if (amount == null || isNaN(amount)) return "0";
  return Math.round(amount).toLocaleString("en-US");
}

const EPP_RATES: Record<number, number> = {
  3: 0,
  6: 5,
  12: 9,
  18: 12,
};

export default function TransactionsPage() {
  const apiClient = useApiClient();
  const { user } = useAuth();

  // Primary state
  const [transactions, setTransactions] = useState<TransactionDto[]>([]);
  const [tabCounts, setTabCounts] = useState<TransactionTabCountsDto>({
    all: 0,
    successful: 0,
    pending: 0,
    failed: 0,
  });
  const [activeTab, setActiveTab] = useState<StatusTab>("ALL");
  const [search, setSearch] = useState("");
  const [institution, setInstitution] = useState("");
  const [method, setMethod] = useState("");
  const [priority, setPriority] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [isExporting, setIsExporting] = useState(false);

  // Detail Modal state
  const [selectedTxId, setSelectedTxId] = useState<string | null>(null);
  const [detailTx, setDetailTx] = useState<TransactionDetailDto | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // Process Payment Wizard state
  const [isPaymentOpen, setIsPaymentOpen] = useState(false);
  const [payStep, setPayStep] = useState<1 | 2 | 3 | 4 | 5>(1);
  const [nationalIdInput, setNationalIdInput] = useState("");
  const [lookupLoading, setLookupLoading] = useState(false);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [customerData, setCustomerData] = useState<CustomerFeesResponse | null>(null);
  const [selectedFeeIds, setSelectedFeeIds] = useState<string[]>([]);
  const [amountInput, setAmountInput] = useState("");
  const [payMethod, setPayMethod] = useState<"ACCOUNT_DEBIT" | "CARD">("ACCOUNT_DEBIT");
  const [isEpp, setIsEpp] = useState(false);
  const [eppTenor, setEppTenor] = useState<number>(6);
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [paymentSuccess, setPaymentSuccess] = useState<BackOfficePaymentResponse | null>(null);

  // Retry Modal state
  const [retryTarget, setRetryTarget] = useState<TransactionDto | null>(null);
  const [retryLoading, setRetryLoading] = useState(false);
  const [retryError, setRetryError] = useState<string | null>(null);
  const [retrySuccessMsg, setRetrySuccessMsg] = useState<string | null>(null);

  // Load Tab Counts
  const loadTabCounts = useCallback(() => {
    apiClient
      .get<TransactionTabCountsDto>("/transactions/tab-counts")
      .then(setTabCounts)
      .catch(() => {});
  }, [apiClient]);

  useEffect(() => {
    loadTabCounts();
  }, [loadTabCounts, refreshTrigger]);

  useEffect(() => {
    let ignore = false;
    const params = new URLSearchParams();
    params.set("page", String(page - 1));
    params.set("pageSize", "10");

    if (activeTab === "SUCCESSFUL") params.set("status", "Successful");
    else if (activeTab === "PENDING") params.set("status", "Pending");
    else if (activeTab === "FAILED") params.set("status", "Failed");

    if (search.trim()) params.set("search", search.trim());
    if (institution) params.set("institution", institution);
    if (method) params.set("method", method);
    if (priority) params.set("priority", priority);
    if (dateFrom) params.set("dateFrom", dateFrom);
    if (dateTo) params.set("dateTo", dateTo);

    apiClient
      .get<PageResponse<TransactionDto>>(`/transactions?${params.toString()}`)
      .then((res) => {
        if (!ignore) {
          setTransactions(res.data ?? []);
          setTotalPages(Math.max(1, res.totalPages ?? 1));
          setTotalElements(res.total ?? 0);
          setLoading(false);
        }
      })
      .catch(() => {
        if (!ignore) {
          setTransactions([]);
          setLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, [apiClient, activeTab, search, institution, method, priority, dateFrom, dateTo, page, refreshTrigger]);

  // Load Transaction Detail
  const openDetail = (id: string) => {
    setSelectedTxId(id);
    setDetailLoading(true);
    apiClient
      .get<TransactionDetailDto>(`/transactions/${id}`)
      .then(setDetailTx)
      .catch(() => setDetailTx(null))
      .finally(() => setDetailLoading(false));
  };

  // Export CSV
  const handleExportCsv = async () => {
    setIsExporting(true);
    try {
      const params = new URLSearchParams();
      if (activeTab === "SUCCESSFUL") params.set("status", "Successful");
      else if (activeTab === "PENDING") params.set("status", "Pending");
      else if (activeTab === "FAILED") params.set("status", "Failed");
      if (search.trim()) params.set("search", search.trim());
      if (institution) params.set("institution", institution);
      if (method) params.set("method", method);
      if (dateFrom) params.set("dateFrom", dateFrom);
      if (dateTo) params.set("dateTo", dateTo);

      const res = await fetch(`http://localhost:8080/api/v1/transactions/export?${params.toString()}`, {
        headers: {
          Authorization: `Bearer ${localStorage.getItem("tuition_access_token") ?? ""}`,
        },
      });
      if (!res.ok) throw new Error("Failed to export transactions");
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `transactions-export-${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Export failed");
    } finally {
      setIsExporting(false);
    }
  };

  // Reset Filters
  const handleResetFilters = () => {
    setSearch("");
    setInstitution("");
    setMethod("");
    setPriority("");
    setDateFrom("");
    setDateTo("");
    setPage(1);
  };

  // Wizard Step 1: Lookup Citizen
  const handleLookupCitizen = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^\d{14}$/.test(nationalIdInput.trim())) {
      setLookupError("National ID must be exactly 14 numeric digits.");
      return;
    }
    setLookupError(null);
    setLookupLoading(true);
    try {
      const res = await apiClient.get<CustomerFeesResponse>(
        `/customers/fees?nationalId=${encodeURIComponent(nationalIdInput.trim())}`
      );
      setCustomerData(res);
      // Auto-select eligible fees
      const eligible = (res.fees ?? []).filter((f) => f.eligible || f.remainingEGP > 0).map((f) => f.id);
      setSelectedFeeIds(eligible);
      const totalRemaining = (res.fees ?? [])
        .filter((f) => eligible.includes(f.id))
        .reduce((sum, f) => sum + (f.remainingEGP ?? 0), 0);
      setAmountInput(totalRemaining > 0 ? String(totalRemaining) : "");
      setPayStep(2);
    } catch (err) {
      setLookupError(err instanceof Error ? err.message : "Citizen fee record not found.");
    } finally {
      setLookupLoading(false);
    }
  };

  // Wizard Step 2: Toggle selected fees
  const toggleFeeSelection = (feeId: string) => {
    const updated = selectedFeeIds.includes(feeId)
      ? selectedFeeIds.filter((id) => id !== feeId)
      : [...selectedFeeIds, feeId];
    setSelectedFeeIds(updated);

    const sum = (customerData?.fees ?? [])
      .filter((f) => updated.includes(f.id))
      .reduce((acc, f) => acc + (f.remainingEGP ?? 0), 0);
    setAmountInput(sum > 0 ? String(sum) : "");
  };

  // Wizard Step 4: Submit Payment
  const handleSubmitPayment = async () => {
    if (!customerData) return;
    const amt = parseFloat(amountInput);
    if (isNaN(amt) || amt <= 0) {
      setPaymentError("Please specify a valid payment amount.");
      return;
    }
    setPaymentLoading(true);
    setPaymentError(null);

    const idempKey = typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : `idemp-${Date.now()}`;
    const payload: BackOfficePaymentRequest = {
      nationalId: customerData.customer.nationalId,
      feeIds: selectedFeeIds,
      amountEGP: amt,
      method: payMethod,
      creditPaymentType: payMethod === "CARD" && isEpp ? "epp" : "full",
      eppTenor: payMethod === "CARD" && isEpp ? eppTenor : undefined,
      processedBy: user?.name ?? "Bank Staff",
    };

    try {
      const res = await apiClient.post<BackOfficePaymentResponse>("/payments", payload, {
        headers: { "Idempotency-Key": idempKey },
      });
      setPaymentSuccess(res);
      setPayStep(5);
      setRefreshTrigger((prev) => prev + 1);
    } catch (err) {
      setPaymentError(err instanceof Error ? err.message : "Payment authorization failed.");
    } finally {
      setPaymentLoading(false);
    }
  };

  // Retry Failed Payment
  const handleRetryPayment = async () => {
    if (!retryTarget) return;
    setRetryLoading(true);
    setRetryError(null);
    const idempKey = typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : `retry-${Date.now()}`;

    try {
      await apiClient.post<TransactionDetailDto>(
        `/payments/${retryTarget.id}/retry`,
        {},
        { headers: { "Idempotency-Key": idempKey } }
      );
      setRetrySuccessMsg(`Transaction ${retryTarget.id.slice(0, 8)} successfully re-authorized!`);
      setTimeout(() => {
        setRetryTarget(null);
        setRetrySuccessMsg(null);
      }, 1800);
      setRefreshTrigger((prev) => prev + 1);
    } catch (err) {
      setRetryError(err instanceof Error ? err.message : "Retry attempt failed.");
    } finally {
      setRetryLoading(false);
    }
  };

  // Close & reset payment modal
  const resetPaymentWizard = () => {
    setIsPaymentOpen(false);
    setPayStep(1);
    setNationalIdInput("");
    setCustomerData(null);
    setSelectedFeeIds([]);
    setAmountInput("");
    setPayMethod("ACCOUNT_DEBIT");
    setIsEpp(false);
    setPaymentError(null);
    setPaymentSuccess(null);
  };

  const selectedFeesSum = (customerData?.fees ?? [])
    .filter((f) => selectedFeeIds.includes(f.id))
    .reduce((sum, f) => sum + (f.remainingEGP ?? 0), 0);

  return (
    <div className="space-y-6 pb-12">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-xl font-bold tracking-tight" style={{ color: "var(--cib-blue)" }}>
            Transactions & Payment Operations
          </h1>
          <p className="text-xs text-gray-500 mt-1">
            Search, audit, and process payments across all affiliated schools and universities.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            variant="secondary"
            size="sm"
            onClick={handleExportCsv}
            disabled={isExporting}
            className="flex items-center gap-1.5"
          >
            <DownloadIcon className="w-4 h-4 text-gray-500" />
            <span>{isExporting ? "Exporting..." : "Export CSV"}</span>
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={() => setIsPaymentOpen(true)}
            className="flex items-center gap-1.5 font-semibold"
            style={{ background: "var(--cib-orange)", borderColor: "var(--cib-orange)" }}
          >
            <PlusIcon className="w-4 h-4" />
            <span>Process Payment</span>
          </Button>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200">
        <nav className="flex space-x-6">
          {[
            { id: "ALL", label: "All Transactions", count: tabCounts.all },
            { id: "SUCCESSFUL", label: "Successful", count: tabCounts.successful },
            { id: "PENDING", label: "Pending", count: tabCounts.pending },
            { id: "FAILED", label: "Failed", count: tabCounts.failed },
          ].map((tab) => {
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => {
                  setActiveTab(tab.id as StatusTab);
                  setPage(1);
                }}
                className={`py-3 px-1 border-b-2 font-medium text-xs flex items-center gap-2 transition-colors ${
                  isActive
                    ? "border-[var(--cib-orange)] text-[var(--cib-blue)] font-semibold"
                    : "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300"
                }`}
              >
                <span>{tab.label}</span>
                <span
                  className={`px-2 py-0.5 rounded-full text-[10px] font-bold ${
                    isActive ? "bg-[var(--cib-orange)] text-white" : "bg-gray-100 text-gray-600"
                  }`}
                >
                  {tab.count.toLocaleString()}
                </span>
              </button>
            );
          })}
        </nav>
      </div>

      {/* Filters Bar */}
      <div className="bg-white p-4 rounded-xl border space-y-3" style={{ borderColor: "#E8EDF5" }}>
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3 text-xs">
          {/* Search */}
          <div className="lg:col-span-2 relative">
            <SearchIcon className="w-4 h-4 absolute left-3 top-2.5 text-gray-400" />
            <input
              type="text"
              placeholder="Search by student, ref, or ID..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(1);
              }}
              className="w-full pl-9 pr-3 py-2 border rounded-lg focus:outline-none focus:ring-1 focus:ring-[var(--cib-blue)] bg-[#F8FAFD]"
              style={{ borderColor: "#E2E8F0" }}
            />
          </div>

          {/* Payment Method */}
          <div>
            <select
              value={method}
              onChange={(e) => {
                setMethod(e.target.value);
                setPage(1);
              }}
              className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-1 focus:ring-[var(--cib-blue)] bg-[#F8FAFD] text-gray-700"
              style={{ borderColor: "#E2E8F0" }}
            >
              <option value="">All Methods</option>
              <option value="ACCOUNT_DEBIT">Account Debit</option>
              <option value="CARD">CIB Card</option>
            </select>
          </div>

          {/* Priority */}
          <div>
            <select
              value={priority}
              onChange={(e) => {
                setPriority(e.target.value);
                setPage(1);
              }}
              className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-1 focus:ring-[var(--cib-blue)] bg-[#F8FAFD] text-gray-700"
              style={{ borderColor: "#E2E8F0" }}
            >
              <option value="">All Priorities</option>
              <option value="OVERDUE">Overdue</option>
              <option value="URGENT">Urgent</option>
              <option value="HIGH">High</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
            </select>
          </div>

          {/* Date From */}
          <div>
            <input
              type="date"
              value={dateFrom}
              onChange={(e) => {
                setDateFrom(e.target.value);
                setPage(1);
              }}
              className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-1 focus:ring-[var(--cib-blue)] bg-[#F8FAFD] text-gray-700"
              style={{ borderColor: "#E2E8F0" }}
            />
          </div>

          {/* Date To */}
          <div>
            <input
              type="date"
              value={dateTo}
              onChange={(e) => {
                setDateTo(e.target.value);
                setPage(1);
              }}
              className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-1 focus:ring-[var(--cib-blue)] bg-[#F8FAFD] text-gray-700"
              style={{ borderColor: "#E2E8F0" }}
            />
          </div>
        </div>

        {(search || institution || method || priority || dateFrom || dateTo) && (
          <div className="flex justify-end pt-2 border-t border-gray-100">
            <button
              onClick={handleResetFilters}
              className="text-xs text-gray-500 hover:text-[var(--cib-orange)] flex items-center gap-1"
            >
              <RefreshIcon className="w-3.5 h-3.5" />
              Reset Filters
            </button>
          </div>
        )}
      </div>

      {/* Transactions Table */}
      <div className="bg-white rounded-xl border overflow-hidden shadow-sm" style={{ borderColor: "#E8EDF5" }}>
        {loading ? (
          <div className="py-20 flex justify-center">
            <LoadingSpinner />
          </div>
        ) : transactions.length === 0 ? (
          <EmptyState
            title="No transactions found"
            description="Try adjusting your filters or searching by a different term."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="bg-[#F8FAFD] border-b border-gray-100">
                  <th className="px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                    Ref / Student
                  </th>
                  <th className="px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                    Institution
                  </th>
                  <th className="px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                    Fee Type
                  </th>
                  <th className="px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                    Amount
                  </th>
                  <th className="px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                    Method
                  </th>
                  <th className="px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                    Due Date & Priority
                  </th>
                  <th className="px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                    Timestamp
                  </th>
                  <th className="px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                    Status
                  </th>
                  <th className="px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider text-right">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {transactions.map((tx) => {
                  const pill = STATUS_PILL_STYLES[tx.status] ?? {
                    bg: "bg-gray-50",
                    text: "text-gray-600",
                    border: "border-gray-200",
                  };
                  return (
                    <tr key={tx.id} className="hover:bg-[#F8FAFD] transition-colors">
                      <td className="px-5 py-3.5">
                        <div className="font-mono text-xs font-bold text-[var(--cib-blue)]">
                          TX-{tx.id.slice(0, 8).toUpperCase()}
                        </div>
                        <div className="text-xs text-gray-700 mt-0.5 font-medium">{tx.student}</div>
                      </td>
                      <td className="px-5 py-3.5 text-xs text-gray-600 whitespace-nowrap">
                        {tx.institution}
                        {tx.institutionType && (
                          <span className="ml-1.5 text-[10px] px-1.5 py-0.5 rounded bg-gray-100 text-gray-500">
                            {tx.institutionType}
                          </span>
                        )}
                      </td>
                      <td className="px-5 py-3.5 text-xs text-gray-600">{tx.feeType}</td>
                      <td className="px-5 py-3.5 whitespace-nowrap">
                        <span className="font-mono text-xs font-bold text-gray-900">
                          EGP {formatMoney(tx.amountEGP)}
                        </span>
                        {tx.partial?.isPartial && (
                          <span className="ml-1.5 text-[10px] px-1.5 py-0.5 bg-sky-50 text-sky-700 border border-sky-200 rounded font-semibold">
                            Partial ({tx.partial.paymentCount}x)
                          </span>
                        )}
                      </td>
                      <td className="px-5 py-3.5 text-xs text-gray-600 whitespace-nowrap">
                        <span className="inline-flex items-center gap-1">
                          {tx.method === "CARD" ? (
                            <CreditCardIcon className="w-3.5 h-3.5 text-[var(--cib-orange)]" />
                          ) : (
                            <BankIcon className="w-3.5 h-3.5 text-[var(--cib-blue)]" />
                          )}
                          {tx.method === "CARD" ? "CIB Card" : "Account Debit"}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 whitespace-nowrap">
                        {tx.dueDate ? (
                          <>
                            <div className="text-xs font-mono text-gray-600">{formatIsoDate(tx.dueDate)}</div>
                            {tx.priority && (
                              <span
                                className={`inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold border mt-0.5 ${
                                  PRIORITY_BADGE_CLASSES[tx.priority] ?? "bg-gray-50 text-gray-600 border-gray-200"
                                }`}
                              >
                                {dueDateLabel(tx.daysToDue ?? 0)}
                              </span>
                            )}
                          </>
                        ) : (
                          <span className="text-gray-400 text-xs">—</span>
                        )}
                      </td>
                      <td className="px-5 py-3.5 text-xs text-gray-500 whitespace-nowrap font-mono">
                        {new Date(tx.timestamp).toLocaleString("en-GB", {
                          day: "numeric",
                          month: "short",
                          year: "numeric",
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </td>
                      <td className="px-5 py-3.5 whitespace-nowrap">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${pill.bg} ${pill.text} ${pill.border}`}
                        >
                          {tx.status}
                        </span>
                      </td>
                      <td className="px-5 py-3.5 text-right whitespace-nowrap space-x-2">
                        <Button
                          variant="secondary"
                          size="sm"
                          onClick={() => openDetail(tx.id)}
                          className="text-xs"
                        >
                          Details
                        </Button>
                        {tx.status === "Failed" && (
                          <Button
                            variant="danger"
                            size="sm"
                            onClick={() => setRetryTarget(tx)}
                            className="text-xs bg-rose-600 hover:bg-rose-700"
                          >
                            Retry
                          </Button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Footer & Pagination */}
        <div className="px-5 py-4 border-t border-gray-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-[#F8FAFD]">
          <span className="text-xs text-gray-500">
            Showing {transactions.length} of {totalElements.toLocaleString()} transactions
          </span>
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={(p) => setPage(p)}
          />
        </div>
      </div>

      {/* Transaction Detail Modal */}
      <Modal
        open={Boolean(selectedTxId)}
        onClose={() => {
          setSelectedTxId(null);
          setDetailTx(null);
        }}
        title="Transaction Audit & Details"
      >
        {detailLoading ? (
          <div className="py-12 flex justify-center">
            <LoadingSpinner />
          </div>
        ) : !detailTx ? (
          <EmptyState title="Could not load details" />
        ) : (
          <div className="space-y-5 text-xs text-gray-700 max-h-[70vh] overflow-y-auto pr-1">
            {/* Meta summary card */}
            <div className="p-4 rounded-xl bg-gray-50 border border-gray-100 grid grid-cols-2 gap-3">
              <div>
                <span className="text-gray-400 block text-[11px]">Transaction ID</span>
                <span className="font-mono font-bold text-[var(--cib-blue)] break-all">{detailTx.id}</span>
              </div>
              <div>
                <span className="text-gray-400 block text-[11px]">Status</span>
                <span
                  className={`inline-block px-2 py-0.5 rounded text-[11px] font-semibold border mt-0.5 ${
                    (STATUS_PILL_STYLES[detailTx.status] ?? {}).bg
                  } ${(STATUS_PILL_STYLES[detailTx.status] ?? {}).text}`}
                >
                  {detailTx.status}
                </span>
              </div>
              <div>
                <span className="text-gray-400 block text-[11px]">Student</span>
                <span className="font-semibold">{detailTx.student}</span>
              </div>
              <div>
                <span className="text-gray-400 block text-[11px]">Institution</span>
                <span className="font-semibold">{detailTx.institution}</span>
              </div>
              <div>
                <span className="text-gray-400 block text-[11px]">Amount Collected</span>
                <span className="font-mono font-bold text-gray-900 text-sm">
                  EGP {formatMoney(detailTx.amountEGP)}
                </span>
              </div>
              <div>
                <span className="text-gray-400 block text-[11px]">Payment Method</span>
                <span className="font-semibold">{detailTx.method === "CARD" ? "CIB Credit/Debit Card" : "Account Debit"}</span>
              </div>
              {detailTx.bankRef && (
                <div>
                  <span className="text-gray-400 block text-[11px]">Bank Ref</span>
                  <span className="font-mono text-gray-600">{detailTx.bankRef}</span>
                </div>
              )}
              {detailTx.idempotencyKey && (
                <div>
                  <span className="text-gray-400 block text-[11px]">Idempotency Key</span>
                  <span className="font-mono text-gray-500 truncate block" title={detailTx.idempotencyKey}>
                    {detailTx.idempotencyKey}
                  </span>
                </div>
              )}
            </div>

            {/* 5-Stage Timeline */}
            <div>
              <h4 className="font-bold text-gray-900 mb-3 text-xs uppercase tracking-wider text-[var(--cib-blue)]">
                Event Timeline
              </h4>
              <div className="relative pl-6 space-y-4 border-l-2 border-gray-200 ml-2">
                {(detailTx.timeline && detailTx.timeline.length > 0
                  ? detailTx.timeline
                  : [
                      { stage: "INITIATED", title: "Payment Initiated", timestamp: detailTx.timestamp, status: "COMPLETED" },
                      { stage: "VALIDATED", title: "Balance & Eligibility Verified", timestamp: detailTx.timestamp, status: "COMPLETED" },
                      { stage: "BANK_AUTH", title: "Bank Authorized", timestamp: detailTx.timestamp, status: detailTx.status === "Failed" ? "FAILED" : "COMPLETED" },
                      { stage: "SETTLED", title: "Fee Ledger Updated", timestamp: detailTx.timestamp, status: detailTx.status === "Successful" ? "COMPLETED" : "PENDING" },
                      { stage: "RECEIPT", title: "Crypto-Receipt Issued", timestamp: detailTx.timestamp, status: detailTx.status === "Successful" ? "COMPLETED" : "PENDING" },
                    ]
                ).map((evt, idx) => (
                  <div key={idx} className="relative">
                    <span
                      className={`absolute -left-[31px] top-0.5 w-4 h-4 rounded-full border-2 bg-white flex items-center justify-center ${
                        evt.status === "COMPLETED"
                          ? "border-emerald-500 bg-emerald-500 text-white"
                          : evt.status === "FAILED"
                          ? "border-rose-500 bg-rose-500 text-white"
                          : "border-gray-300"
                      }`}
                    >
                      {evt.status === "COMPLETED" && <span className="w-1.5 h-1.5 bg-white rounded-full" />}
                    </span>
                    <div className="font-semibold text-gray-800">{evt.title}</div>
                    <div className="text-[11px] text-gray-400 font-mono">
                      {evt.timestamp ? new Date(evt.timestamp).toLocaleString("en-GB") : "Pending"}
                    </div>
                  </div>
                ))}
              </div>
            </div>

            {/* Allocated Dues Breakdown */}
            {detailTx.allocations && detailTx.allocations.length > 0 && (
              <div>
                <h4 className="font-bold text-gray-900 mb-2 text-xs uppercase tracking-wider text-[var(--cib-blue)]">
                  Allocated Fee Lines
                </h4>
                <div className="border border-gray-200 rounded-lg overflow-hidden">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-gray-50 border-b border-gray-200">
                      <tr>
                        <th className="px-3 py-2 text-gray-500">Fee</th>
                        <th className="px-3 py-2 text-gray-500 text-right">Allocated</th>
                        <th className="px-3 py-2 text-gray-500 text-right">Remaining</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {detailTx.allocations.map((a, i) => (
                        <tr key={i}>
                          <td className="px-3 py-2">{a.feeName}</td>
                          <td className="px-3 py-2 text-right font-mono font-bold text-emerald-700">
                            EGP {formatMoney(a.allocatedAmountEGP)}
                          </td>
                          <td className="px-3 py-2 text-right font-mono text-gray-500">
                            EGP {formatMoney(a.remainingAmountEGP)}
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

      {/* Multi-Step Process Payment Modal */}
      <Modal
        open={isPaymentOpen}
        onClose={resetPaymentWizard}
        title={
          payStep === 5
            ? "Payment Receipt"
            : `Process Citizen Tuition Payment (Step ${payStep} of 4)`
        }
      >
        <div className="text-xs space-y-4 max-h-[70vh] overflow-y-auto pr-1">
          {/* Step 1: Citizen Lookup */}
          {payStep === 1 && (
            <form onSubmit={handleLookupCitizen} className="space-y-4">
              <p className="text-gray-500">
                Enter the citizen&apos;s 14-digit Egyptian National ID to retrieve all verified student enrollments and open fee lines.
              </p>
              <div>
                <label className="block text-gray-700 font-semibold mb-1">
                  14-Digit National ID
                </label>
                <div className="relative">
                  <input
                    type="text"
                    maxLength={14}
                    placeholder="e.g. 29805150101023"
                    value={nationalIdInput}
                    onChange={(e) => setNationalIdInput(e.target.value.replace(/\D/g, ""))}
                    className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-1 focus:ring-[var(--cib-blue)] font-mono text-sm"
                    style={{ borderColor: "#CBD5E1" }}
                    autoFocus
                  />
                </div>
                {lookupError && (
                  <p className="text-red-600 text-[11px] mt-1.5 font-medium">{lookupError}</p>
                )}
              </div>
              <div className="p-3 bg-blue-50/60 border border-blue-100 rounded-lg text-blue-900 text-[11px]">
                💡 <strong>Demo tip:</strong> Use test National ID <code className="font-bold">29805150101023</code> to look up pre-seeded Cairo International School tuition lines.
              </div>
              <div className="flex justify-end gap-2 pt-3 border-t border-gray-100">
                <Button variant="secondary" size="sm" type="button" onClick={resetPaymentWizard}>
                  Cancel
                </Button>
                <Button variant="primary" size="sm" type="submit" loading={lookupLoading}>
                  Search Open Fees
                </Button>
              </div>
            </form>
          )}

          {/* Step 2: Select Open Dues & Amount */}
          {payStep === 2 && customerData && (
            <div className="space-y-4">
              <div className="p-3 bg-gray-50 border border-gray-100 rounded-lg flex items-center justify-between">
                <div>
                  <span className="font-bold text-gray-900 block">{customerData.customer.fullName}</span>
                  <span className="font-mono text-gray-500 text-[11px]">
                    NID: {customerData.customer.nationalId}
                  </span>
                </div>
                {customerData.customer.cibCustomer && (
                  <Badge tone="success">Verified CIB Customer</Badge>
                )}
              </div>

              <div>
                <h4 className="font-bold text-gray-800 mb-2">Select Fee Lines to Pay</h4>
                <div className="space-y-2 border border-gray-200 rounded-lg p-2 max-h-44 overflow-y-auto">
                  {customerData.fees.map((fee) => {
                    const checked = selectedFeeIds.includes(fee.id);
                    return (
                      <label
                        key={fee.id}
                        className={`flex items-start gap-3 p-2 rounded-lg cursor-pointer transition-colors border ${
                          checked ? "bg-orange-50/40 border-orange-200" : "hover:bg-gray-50 border-transparent"
                        }`}
                      >
                        <input
                          type="checkbox"
                          checked={checked}
                          onChange={() => toggleFeeSelection(fee.id)}
                          className="mt-1 rounded text-[var(--cib-orange)] focus:ring-[var(--cib-orange)]"
                        />
                        <div className="flex-1 min-w-0">
                          <div className="flex justify-between items-center">
                            <span className="font-semibold text-gray-800">{fee.name}</span>
                            <span className="font-mono font-bold text-[var(--cib-blue)]">
                              EGP {formatMoney(fee.remainingEGP)}
                            </span>
                          </div>
                          <div className="text-[11px] text-gray-500 flex justify-between mt-0.5">
                            <span>Original: EGP {formatMoney(fee.originalAmountEGP)}</span>
                            {fee.dueDate && (
                              <span className="text-orange-700">Due: {formatIsoDate(fee.dueDate)}</span>
                            )}
                          </div>
                        </div>
                      </label>
                    );
                  })}
                </div>
              </div>

              <div>
                <div className="flex justify-between items-center mb-1">
                  <label className="font-semibold text-gray-700">Payment Amount (EGP)</label>
                  <span className="text-gray-400 text-[11px]">
                    Max remaining: EGP {formatMoney(selectedFeesSum)}
                  </span>
                </div>
                <input
                  type="number"
                  min="1"
                  max={selectedFeesSum}
                  value={amountInput}
                  onChange={(e) => setAmountInput(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg focus:outline-none focus:ring-1 focus:ring-[var(--cib-blue)] font-mono text-sm font-bold"
                  style={{ borderColor: "#CBD5E1" }}
                />
              </div>

              <div className="flex justify-between pt-3 border-t border-gray-100">
                <Button variant="secondary" size="sm" onClick={() => setPayStep(1)}>
                  Back
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  disabled={selectedFeeIds.length === 0 || !amountInput || parseFloat(amountInput) <= 0}
                  onClick={() => setPayStep(3)}
                >
                  Next: Payment Method
                </Button>
              </div>
            </div>
          )}

          {/* Step 3: Payment Method & EPP */}
          {payStep === 3 && (
            <div className="space-y-4">
              <h4 className="font-bold text-gray-800">Select Payment Instrument</h4>
              <div className="grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => {
                    setPayMethod("ACCOUNT_DEBIT");
                    setIsEpp(false);
                  }}
                  className={`p-3 rounded-xl border text-left flex flex-col gap-2 transition-all ${
                    payMethod === "ACCOUNT_DEBIT"
                      ? "border-[var(--cib-blue)] bg-blue-50/40 ring-1 ring-[var(--cib-blue)]"
                      : "border-gray-200 hover:bg-gray-50"
                  }`}
                >
                  <BankIcon className="w-5 h-5 text-[var(--cib-blue)]" />
                  <div>
                    <div className="font-bold text-gray-900">CIB Account Debit</div>
                    <div className="text-[11px] text-gray-500">Direct core banking debit</div>
                  </div>
                </button>

                <button
                  type="button"
                  onClick={() => setPayMethod("CARD")}
                  className={`p-3 rounded-xl border text-left flex flex-col gap-2 transition-all ${
                    payMethod === "CARD"
                      ? "border-[var(--cib-orange)] bg-orange-50/40 ring-1 ring-[var(--cib-orange)]"
                      : "border-gray-200 hover:bg-gray-50"
                  }`}
                >
                  <CreditCardIcon className="w-5 h-5 text-[var(--cib-orange)]" />
                  <div>
                    <div className="font-bold text-gray-900">CIB Card Payment</div>
                    <div className="text-[11px] text-gray-500">Credit / Debit or EPP Plan</div>
                  </div>
                </button>
              </div>

              {payMethod === "CARD" && (
                <div className="p-3 border border-orange-200 bg-orange-50/20 rounded-xl space-y-3">
                  <div className="flex items-center justify-between">
                    <label className="font-semibold text-gray-800 cursor-pointer flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={isEpp}
                        onChange={(e) => setIsEpp(e.target.checked)}
                        className="rounded text-[var(--cib-orange)] focus:ring-[var(--cib-orange)]"
                      />
                      Convert to Equal Payment Plan (EPP)
                    </label>
                    <span className="text-[10px] px-1.5 py-0.5 rounded bg-orange-100 text-[var(--cib-orange-dark)] font-bold">
                      Credit Cards Only
                    </span>
                  </div>

                  {isEpp && (
                    <div className="space-y-2 pt-2 border-t border-orange-100">
                      <span className="text-[11px] text-gray-500">Select installment tenor:</span>
                      <div className="grid grid-cols-4 gap-2">
                        {[3, 6, 12, 18].map((t) => {
                          const interest = EPP_RATES[t] ?? 0;
                          const monthly = Math.round(((parseFloat(amountInput) || 0) * (1 + interest / 100)) / t);
                          return (
                            <button
                              key={t}
                              type="button"
                              onClick={() => setEppTenor(t)}
                              className={`p-2 rounded-lg border text-center transition-all ${
                                eppTenor === t
                                  ? "border-[var(--cib-orange)] bg-white font-bold shadow-sm"
                                  : "border-gray-200 bg-white/60 text-gray-600"
                              }`}
                            >
                              <div className="text-xs font-bold">{t} Mos</div>
                              <div className="text-[10px] text-gray-500">{interest}% fee</div>
                              <div className="text-[10px] font-mono text-[var(--cib-blue)] mt-0.5">
                                ~EGP {formatMoney(monthly)}/mo
                              </div>
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  )}
                </div>
              )}

              <div className="flex justify-between pt-3 border-t border-gray-100">
                <Button variant="secondary" size="sm" onClick={() => setPayStep(2)}>
                  Back
                </Button>
                <Button variant="primary" size="sm" onClick={() => setPayStep(4)}>
                  Next: Review & Authorize
                </Button>
              </div>
            </div>
          )}

          {/* Step 4: Review & Confirm */}
          {payStep === 4 && customerData && (
            <div className="space-y-4">
              <div className="p-4 bg-gray-50 border border-gray-100 rounded-xl space-y-2">
                <div className="flex justify-between pb-2 border-b border-gray-200">
                  <span className="text-gray-500">Citizen:</span>
                  <span className="font-semibold text-gray-900">{customerData.customer.fullName}</span>
                </div>
                <div className="flex justify-between pb-2 border-b border-gray-200">
                  <span className="text-gray-500">National ID:</span>
                  <span className="font-mono text-gray-700">{customerData.customer.nationalId}</span>
                </div>
                <div className="flex justify-between pb-2 border-b border-gray-200">
                  <span className="text-gray-500">Selected Fees:</span>
                  <span className="font-semibold text-gray-800">{selectedFeeIds.length} item(s)</span>
                </div>
                <div className="flex justify-between pb-2 border-b border-gray-200">
                  <span className="text-gray-500">Payment Instrument:</span>
                  <span className="font-semibold text-gray-800">
                    {payMethod === "ACCOUNT_DEBIT" ? "CIB Account Debit" : isEpp ? `CIB Card (${eppTenor} Mo EPP)` : "CIB Card (Full)"}
                  </span>
                </div>
                <div className="flex justify-between items-center pt-1 text-sm font-bold text-[var(--cib-blue)]">
                  <span>Total Amount to Charge:</span>
                  <span className="font-mono text-base text-[var(--cib-orange-dark)]">
                    EGP {formatMoney(parseFloat(amountInput))}
                  </span>
                </div>
              </div>

              <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-[11px] text-amber-800">
                ⚠️ <strong>Financial Policy:</strong> Payments are processed directly through the core banking integration. Transactions are irrevocable and cannot be refunded from the back-office.
              </div>

              {paymentError && (
                <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-[11px] text-red-700 font-medium">
                  {paymentError}
                </div>
              )}

              <div className="flex justify-between pt-3 border-t border-gray-100">
                <Button variant="secondary" size="sm" onClick={() => setPayStep(3)} disabled={paymentLoading}>
                  Back
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  loading={paymentLoading}
                  onClick={handleSubmitPayment}
                  style={{ background: "var(--cib-orange)", borderColor: "var(--cib-orange)" }}
                >
                  Authorize Payment Now
                </Button>
              </div>
            </div>
          )}

          {/* Step 5: Success & Receipt */}
          {payStep === 5 && paymentSuccess && (
            <div className="space-y-4 text-center py-4">
              <div className="w-12 h-12 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto">
                <CheckCircleIcon className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-gray-900">Payment Authorized Successfully</h3>
                <p className="text-gray-500 text-xs mt-0.5">
                  The tuition fee ledger and core banking balances have been updated.
                </p>
              </div>

              <div className="p-4 bg-gray-50 border border-gray-100 rounded-xl text-left space-y-1.5 font-mono text-xs">
                <div className="flex justify-between">
                  <span className="text-gray-500 font-sans">Transaction ID:</span>
                  <span className="font-bold text-[var(--cib-blue)]">{paymentSuccess.transactionId}</span>
                </div>
                {paymentSuccess.bankRef && (
                  <div className="flex justify-between">
                    <span className="text-gray-500 font-sans">Bank Reference:</span>
                    <span>{paymentSuccess.bankRef}</span>
                  </div>
                )}
                {paymentSuccess.authCode && (
                  <div className="flex justify-between">
                    <span className="text-gray-500 font-sans">Authorization Code:</span>
                    <span>{paymentSuccess.authCode}</span>
                  </div>
                )}
                <div className="flex justify-between">
                  <span className="text-gray-500 font-sans">Amount Paid:</span>
                  <span className="font-bold text-emerald-700">EGP {formatMoney(paymentSuccess.amountPaidEGP)}</span>
                </div>
                {paymentSuccess.isPartial && (
                  <div className="flex justify-between">
                    <span className="text-gray-500 font-sans">Remaining Balance:</span>
                    <span className="text-orange-700">EGP {formatMoney(paymentSuccess.remainingBalanceEGP)}</span>
                  </div>
                )}
              </div>

              <div className="pt-2 flex justify-center gap-3">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    window.open(
                      `http://localhost:8080/api/v1/payments/${paymentSuccess.transactionId}/receipt?format=pdf`,
                      "_blank"
                    );
                  }}
                >
                  <DownloadIcon className="w-4 h-4" />
                  Download PDF Receipt
                </Button>
                <Button variant="primary" size="sm" onClick={resetPaymentWizard}>
                  Done
                </Button>
              </div>
            </div>
          )}
        </div>
      </Modal>

      {/* Retry Failed Payment Modal */}
      <Modal
        open={Boolean(retryTarget)}
        onClose={() => {
          setRetryTarget(null);
          setRetryError(null);
        }}
        title="Retry Failed Transaction"
      >
        {retryTarget && (
          <div className="space-y-4 text-xs text-gray-700">
            <p>
              Are you sure you want to re-authorize payment for student{" "}
              <strong>{retryTarget.student}</strong> in the amount of{" "}
              <strong>EGP {formatMoney(retryTarget.amountEGP)}</strong>?
            </p>
            <p className="text-gray-500 text-[11px]">
              A new unique idempotency key will be attached to prevent duplicate billing.
            </p>
            {retryError && (
              <div className="p-3 bg-red-50 border border-red-200 rounded text-red-700">
                {retryError}
              </div>
            )}
            {retrySuccessMsg && (
              <div className="p-3 bg-emerald-50 border border-emerald-200 rounded text-emerald-700 font-semibold">
                {retrySuccessMsg}
              </div>
            )}
            <div className="flex justify-end gap-2 pt-3 border-t border-gray-100">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setRetryTarget(null)}
                disabled={retryLoading}
              >
                Cancel
              </Button>
              <Button
                variant="danger"
                size="sm"
                loading={retryLoading}
                onClick={handleRetryPayment}
                className="bg-rose-600 hover:bg-rose-700"
              >
                Confirm & Retry
              </Button>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
