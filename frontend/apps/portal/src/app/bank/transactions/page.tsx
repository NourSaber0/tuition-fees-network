"use client";

import { useEffect, useState, useCallback } from "react";
import { useApiClient, useAuth, type PageResponse } from "@tuition/api-client";
import {
  Button,
  LoadingSpinner,
  EmptyState,
  Pagination,
  Modal,
  SearchIcon,
  DownloadIcon,
  PlusIcon,
  RefreshIcon,
  CheckCircleIcon,
  ClockIcon,
  AlertIcon,
  UserIcon,
  CreditCardIcon,
  ChevronLeftIcon,
  CheckIcon,
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
type MainView = "list" | "detail" | "workflow";
type PayMethodChoice = "ACCOUNT_DEBIT" | "CARD";

const STATUS_PILL_STYLES: Record<string, { bg: string; text: string; border: string }> = {
  Successful: { bg: "bg-emerald-50", text: "text-emerald-700", border: "border-emerald-200" },
  Pending: { bg: "bg-amber-50", text: "text-amber-700", border: "border-amber-200" },
  Failed: { bg: "bg-rose-50", text: "text-rose-700", border: "border-rose-200" },
};

function formatMoney(amount: number | undefined): string {
  if (amount == null || isNaN(amount)) return "0";
  return Math.round(amount).toLocaleString("en-US");
}

function maskNID(nid?: string): string {
  if (!nid || nid.length < 7) return nid || "—";
  return nid.slice(0, 3) + "•••••••" + nid.slice(-4);
}

export default function TransactionsPage() {
  const apiClient = useApiClient();
  const { user } = useAuth();

  // Navigation / View State
  const [mainView, setMainView] = useState<MainView>("list");

  // List View State
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

  // Detail View State
  const [detailTx, setDetailTx] = useState<TransactionDetailDto | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // Workflow / Process Payment Wizard State
  const [payStep, setPayStep] = useState<1 | 2 | 3 | 4 | 5>(1);
  const [receiptRef, setReceiptRef] = useState("");
  const [receiptDate, setReceiptDate] = useState("");
  const [nationalIdInput, setNationalIdInput] = useState("");
  const [lookupLoading, setLookupLoading] = useState(false);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [customerData, setCustomerData] = useState<CustomerFeesResponse | null>(null);
  const [selectedFeeIds, setSelectedFeeIds] = useState<string[]>([]);
  const [amountInput, setAmountInput] = useState("");
  const [amountError, setAmountError] = useState<string | null>(null);
  const [payMethod, setPayMethod] = useState<PayMethodChoice>("ACCOUNT_DEBIT");
  const [isEpp, setIsEpp] = useState(false);
  const [eppTenor, setEppTenor] = useState<number>(6);
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [paymentSuccess, setPaymentSuccess] = useState<BackOfficePaymentResponse | null>(null);

  // Retry Modal State
  const [retryTarget, setRetryTarget] = useState<TransactionDto | null>(null);
  const [retryLoading, setRetryLoading] = useState(false);
  const [retryError, setRetryError] = useState<string | null>(null);
  const [retrySuccessMsg, setRetrySuccessMsg] = useState<string | null>(null);

  // Load Tab Counts
  const loadTabCounts = useCallback(() => {
    apiClient
      .get<Record<string, number>>("/transactions/tab-counts")
      .then((data) => {
        if (!data) return;
        setTabCounts({
          all: Number(data.All ?? data.all ?? 0),
          successful: Number(data.Successful ?? data.successful ?? 0),
          pending: Number(data.Pending ?? data.pending ?? 0),
          failed: Number(data.Failed ?? data.failed ?? 0),
        });
      })
      .catch(() => {});
  }, [apiClient]);

  useEffect(() => {
    loadTabCounts();
  }, [loadTabCounts, refreshTrigger]);

  // Load Transactions List
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

  // Load Transaction Detail View
  const handleOpenDetail = (tx: TransactionDto) => {
    setDetailTx(null);
    setDetailLoading(true);
    setMainView("detail");

    apiClient
      .get<TransactionDetailDto>(`/transactions/${tx.id}`)
      .then(setDetailTx)
      .catch(() => {
        // Fallback to tx summary data if endpoint encounters error
        setDetailTx({
          id: tx.id,
          institution: tx.institution,
          institutionType: tx.institutionType,
          student: tx.student,
          feeType: tx.feeType,
          amountEGP: tx.amountEGP,
          method: tx.method,
          status: tx.status,
          bankRef: tx.bankRef,
          settlementStatus: tx.settlementStatus ?? "Settled",
          reconStatus: tx.reconStatus ?? "Matched",
          timestamp: tx.timestamp,
          partial: tx.partial,
          idempotencyKey: tx.idempotencyKey,
          dueDate: tx.dueDate,
          priority: tx.priority,
          penaltyEGP: tx.penaltyEGP,
          outstandingEGP: tx.outstandingEGP,
          totalDueEGP: tx.totalDueEGP,
        });
      })
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
  const handleLookupCitizen = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const cleanNid = nationalIdInput.trim();
    if (!/^\d{14}$/.test(cleanNid)) {
      setLookupError("National ID must be exactly 14 numeric digits.");
      return;
    }
    setLookupError(null);
    setLookupLoading(true);
    try {
      const res = await apiClient.get<CustomerFeesResponse>(
        `/customers/fees?nationalId=${encodeURIComponent(cleanNid)}`
      );
      setCustomerData(res);
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
      nationalId: customerData.customer.nationalId || nationalIdInput.trim(),
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
      setReceiptRef(res.receiptRef || res.bankRef || res.transactionId || `TX-${idempKey.slice(0, 8).toUpperCase()}`);
      setReceiptDate(
        new Date().toLocaleDateString("en-GB", {
          day: "numeric",
          month: "short",
          year: "numeric",
        }) +
          " · " +
          new Date().toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })
      );
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

  // Reset payment wizard
  const resetPaymentWizard = () => {
    setPayStep(1);
    setNationalIdInput("");
    setReceiptRef("");
    setReceiptDate("");
    setCustomerData(null);
    setSelectedFeeIds([]);
    setAmountInput("");
    setAmountError(null);
    setPayMethod("ACCOUNT_DEBIT");
    setIsEpp(false);
    setPaymentError(null);
    setPaymentSuccess(null);
  };

  const selectedFeesSum = (customerData?.fees ?? [])
    .filter((f) => selectedFeeIds.includes(f.id))
    .reduce((sum, f) => sum + (f.remainingEGP ?? 0), 0);
  const payAmountNum = parseFloat(amountInput) || 0;
  const isPartial = payAmountNum > 0 && payAmountNum < selectedFeesSum;

  /* ─────────────────────────────────────────────────────────────
     VIEW 1: TRANSACTION DETAIL (Figma 1:1)
  ───────────────────────────────────────────────────────────── */
  if (mainView === "detail" && (detailTx || detailLoading)) {
    if (detailLoading || !detailTx) {
      return (
        <div className="flex flex-col items-center justify-center py-24 space-y-3">
          <LoadingSpinner />
          <p className="text-sm text-gray-500">Loading transaction details...</p>
        </div>
      );
    }

    const outstanding = detailTx.outstandingEGP ?? (detailTx.partial ? detailTx.partial.remainingAmountEGP : 0);
    const penalty = detailTx.penaltyEGP ?? 0;
    const totalDue = detailTx.totalDueEGP ?? outstanding + penalty;
    const isOverdue = detailTx.priority === "OVERDUE";

    return (
      <div className="space-y-4">
        {/* Back link */}
        <div className="flex items-center gap-3">
          <button
            onClick={() => setMainView("list")}
            className="flex items-center gap-1.5 text-sm text-gray-400 hover:text-gray-700 transition-colors"
          >
            <ChevronLeftIcon className="w-4 h-4" /> Back to Transactions
          </button>
          <span className="text-[#DDE4EE]">|</span>
          <span className="text-sm font-semibold text-[#003087]">Transaction Detail</span>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
          {/* Main details column (2 cols) */}
          <div className="lg:col-span-2 space-y-4">
            <div className="bg-white rounded-xl border border-[#E8EDF5] p-6 shadow-xs">
              <div className="flex items-start justify-between mb-5 pb-5 border-b border-gray-100">
                <div>
                  <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">
                    Official Reference
                  </div>
                  <h2 className="text-lg font-bold text-[#1B2A4A] font-mono">
                    {detailTx.bankRef || `TXN-${detailTx.id.slice(0, 8).toUpperCase()}`}
                  </h2>
                  <p className="text-xs text-gray-400 mt-0.5">{detailTx.timestamp}</p>
                </div>
                <span
                  className={`inline-flex items-center px-2.5 py-1 rounded text-xs font-semibold border ${
                    STATUS_PILL_STYLES[detailTx.status]?.bg ?? "bg-gray-100"
                  } ${STATUS_PILL_STYLES[detailTx.status]?.text ?? "text-gray-700"} ${
                    STATUS_PILL_STYLES[detailTx.status]?.border ?? "border-gray-200"
                  }`}
                >
                  {detailTx.status}
                </span>
              </div>

              <div className="grid grid-cols-2 gap-x-8 gap-y-4">
                {[
                  ["Institution", detailTx.institution],
                  ["Institution Type", detailTx.institutionType || "School"],
                  ["Student", detailTx.student],
                  ["Fee Type", detailTx.feeType],
                  ["Amount", `EGP ${formatMoney(detailTx.amountEGP)}`],
                  ["Payment Method", detailTx.method],
                  ["Bank Reference", detailTx.bankRef || "BNK-CIB-84720"],
                  ["Settlement Status", detailTx.settlementStatus || "Settled"],
                  ["Reconciliation Status", detailTx.reconStatus || "Matched"],
                ].map(([k, v]) => (
                  <div key={k}>
                    <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">
                      {k}
                    </div>
                    <div
                      className={`text-sm ${
                        k === "Amount"
                          ? "font-bold text-[#1B2A4A] font-mono text-base"
                          : k === "Bank Reference"
                          ? "font-mono text-[#003087]"
                          : "text-gray-800"
                      }`}
                    >
                      {v}
                    </div>
                  </div>
                ))}
              </div>

              {/* Partial payment breakdown */}
              {detailTx.partial && (
                <div className="mt-6 pt-5 border-t border-gray-100">
                  <div className="flex items-center gap-2 mb-3">
                    <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                      Payment Breakdown
                    </div>
                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded border bg-amber-50 text-amber-700 border-amber-200">
                      Partial Payment
                    </span>
                  </div>
                  <div className="bg-[#F8FAFD] rounded-xl border border-[#E8EDF5] p-4 space-y-3">
                    <div className="flex justify-between text-sm">
                      <span className="text-gray-500">Original Fee Amount</span>
                      <span className="font-mono font-semibold text-gray-700">
                        EGP {formatMoney(detailTx.partial.totalFeeAmountEGP)}
                      </span>
                    </div>
                    {detailTx.partial.previouslyPaidEGP > 0 && (
                      <div className="flex justify-between text-sm">
                        <span className="text-gray-500">Previously Paid</span>
                        <span className="font-mono font-semibold text-green-700">
                          − EGP {formatMoney(detailTx.partial.previouslyPaidEGP)}
                        </span>
                      </div>
                    )}
                    <div className="flex justify-between text-sm border-t border-gray-100 pt-3">
                      <span className="text-gray-500">This Payment</span>
                      <span className="font-mono font-semibold text-[#003087]">
                        EGP {formatMoney(detailTx.amountEGP)}
                      </span>
                    </div>
                    <div className="flex justify-between text-sm border-t border-gray-100 pt-3">
                      <span className="font-semibold text-gray-700">Remaining Balance</span>
                      <span className="font-mono font-bold text-amber-700">
                        EGP {formatMoney(detailTx.partial.remainingAmountEGP)}
                      </span>
                    </div>
                    <div className="pt-1">
                      <div className="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
                        <div
                          className="h-full bg-[#003087] rounded-full transition-all"
                          style={{
                            width: `${Math.min(
                              100,
                              Math.round(
                                ((detailTx.partial.previouslyPaidEGP + detailTx.amountEGP) /
                                  (detailTx.partial.totalFeeAmountEGP || 1)) *
                                  100
                              )
                            )}%`,
                          }}
                        />
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* Deadline & Penalty card */}
              {detailTx.dueDate && (
                <div className="mt-6 pt-5 border-t border-gray-100">
                  <div className="flex items-center gap-2 mb-3">
                    <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                      Payment Deadline & Penalties
                    </div>
                    {isOverdue && (
                      <span className="text-[11px] font-bold text-red-700 bg-red-50 border border-red-200 rounded px-2 py-0.5">
                        ⚠ Overdue
                      </span>
                    )}
                  </div>
                  <div
                    className={`rounded-xl border overflow-hidden ${
                      isOverdue ? "border-red-200" : "border-[#E8EDF5]"
                    }`}
                  >
                    <div
                      className={`flex items-center justify-between px-4 py-3 ${
                        isOverdue ? "bg-red-50" : "bg-[#F8FAFD]"
                      }`}
                    >
                      <div>
                        <div className="text-sm font-semibold text-[#1B2A4A]">
                          Due: {formatIsoDate(detailTx.dueDate)}
                        </div>
                        <div className="text-xs text-gray-400 mt-0.5">
                          {detailTx.daysToDue != null ? dueDateLabel(detailTx.daysToDue) : "Due soon"}
                        </div>
                      </div>
                      {detailTx.priority && (
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${
                            PRIORITY_BADGE_CLASSES[detailTx.priority] ?? "bg-gray-100 text-gray-600"
                          }`}
                        >
                          {detailTx.priority}
                        </span>
                      )}
                    </div>
                    <div className="px-4 py-3 space-y-2.5 bg-white">
                      <div className="flex justify-between text-xs">
                        <span className="text-gray-500">Outstanding Balance</span>
                        <span className="font-mono font-semibold text-gray-700">
                          EGP {formatMoney(outstanding)}
                        </span>
                      </div>
                      {penalty > 0 && (
                        <div className="flex justify-between text-xs">
                          <span className="text-red-600 font-semibold">Late Penalty (5%)</span>
                          <span className="font-mono font-bold text-red-600">
                            + EGP {formatMoney(penalty)}
                          </span>
                        </div>
                      )}
                      <div className="flex justify-between text-sm border-t pt-2.5">
                        <span className="font-semibold text-gray-700">Total Due</span>
                        <span className="font-mono font-bold text-[#003087]">
                          EGP {formatMoney(totalDue)}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Right column: 5-Stage Timeline (1 col) */}
          <div className="space-y-4">
            <div className="bg-white rounded-xl border border-[#E8EDF5] p-5 shadow-xs">
              <h3 className="text-sm font-semibold text-[#1B2A4A] mb-4">Payment Timeline</h3>
              <div className="space-y-4">
                {[
                  { label: "Payment Initiated", time: "14:28:01", done: true },
                  { label: "Bank Authorisation", time: "14:28:03", done: true },
                  {
                    label: "Payment Captured",
                    time: "14:28:04",
                    done: detailTx.status === "Successful",
                  },
                  {
                    label: "Settlement",
                    time: detailTx.settlementStatus === "Settled" ? "14:30:00" : "—",
                    done: detailTx.settlementStatus === "Settled",
                  },
                  {
                    label: "Reconciliation",
                    time: detailTx.reconStatus === "Matched" ? "14:35:00" : "—",
                    done: detailTx.reconStatus === "Matched",
                  },
                ].map((item, idx) => (
                  <div key={item.label} className="flex items-start gap-3 text-xs">
                    <div
                      className={`w-5 h-5 rounded-full flex items-center justify-center shrink-0 mt-0.5 ${
                        item.done ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-400"
                      }`}
                    >
                      {item.done ? <CheckIcon className="w-3 h-3" /> : idx + 1}
                    </div>
                    <div className="flex-1">
                      <div className={`font-semibold ${item.done ? "text-gray-800" : "text-gray-400"}`}>
                        {item.label}
                      </div>
                      <div className="text-[10px] text-gray-400 font-mono mt-0.5">{item.time}</div>
                    </div>
                  </div>
                ))}
              </div>
              <div className="mt-5 pt-4 border-t border-gray-100 space-y-2">
                <div className="text-[11px] text-gray-400">
                  <strong className="text-gray-600">Channel:</strong> CIB Online Banking
                </div>
                <div className="text-[11px] text-gray-400">
                  <strong className="text-gray-600">Idempotency Key:</strong>
                  <span className="font-mono block text-[10px] mt-0.5 text-gray-600 truncate">
                    {detailTx.idempotencyKey || `${detailTx.id.slice(0, 8)}-KEY`}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    );
  }

  /* ─────────────────────────────────────────────────────────────
     VIEW 2: PAYMENT WORKFLOW (Figma 1:1)
  ───────────────────────────────────────────────────────────── */
  if (mainView === "workflow") {
    const steps = ["Search", "Select Fees", "Payment", "Review", "Done"];

    return (
      <div className="space-y-4">
        {/* Header link */}
        <div className="flex items-center gap-3">
          <button
            onClick={() => {
              resetPaymentWizard();
              setMainView("list");
            }}
            className="flex items-center gap-1.5 text-sm text-gray-400 hover:text-gray-700 transition-colors"
          >
            <ChevronLeftIcon className="w-4 h-4" /> Back to Transactions
          </button>
          <span className="text-[#DDE4EE]">|</span>
          <span className="text-sm font-semibold text-[#003087]">Process Customer Payment</span>
        </div>

        <div className="bg-white rounded-xl border border-[#E8EDF5] p-6 shadow-xs">
          {/* Stepper */}
          {payStep <= 4 && (
            <div className="flex items-center gap-0 mb-6">
              {steps.map((s, i) => (
                <div key={s} className="flex items-center flex-1 last:flex-none">
                  <div className="flex items-center gap-1.5 shrink-0">
                    <div
                      className={`w-6 h-6 rounded-full flex items-center justify-center text-[11px] font-bold transition-all ${
                        i < payStep - 1
                          ? "bg-[#003087] text-white"
                          : i === payStep - 1
                          ? "bg-[#003087] text-white ring-4 ring-[#003087]/20"
                          : "bg-gray-100 text-gray-400"
                      }`}
                    >
                      {i < payStep - 1 ? <CheckIcon className="w-3 h-3" /> : i + 1}
                    </div>
                    <span
                      className={`text-xs font-semibold whitespace-nowrap ${
                        i === payStep - 1
                          ? "text-[#003087]"
                          : i < payStep - 1
                          ? "text-gray-600"
                          : "text-gray-400"
                      }`}
                    >
                      {s}
                    </span>
                  </div>
                  {i < steps.length - 1 && (
                    <div
                      className={`flex-1 h-px mx-2 ${
                        i < payStep - 1 ? "bg-[#003087]" : "bg-gray-200"
                      }`}
                    />
                  )}
                </div>
              ))}
            </div>
          )}

          {/* ── Step 1: NID Search ── */}
          {payStep === 1 && (
            <div className="max-w-md mx-auto py-4">
              <div className="text-center mb-6">
                <div className="w-12 h-12 rounded-xl bg-[#003087]/10 flex items-center justify-center mx-auto mb-3">
                  <UserIcon className="w-6 h-6 text-[#003087]" />
                </div>
                <h3 className="text-base font-bold text-[#1B2A4A]">Customer Identification</h3>
                <p className="text-sm text-gray-400 mt-1">
                  Enter the customer&apos;s National ID to retrieve their fee information.
                </p>
              </div>

              <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">
                National ID *
              </label>
              <div className="flex gap-2">
                <input
                  type="text"
                  maxLength={14}
                  placeholder="14-digit National ID"
                  value={nationalIdInput}
                  onChange={(e) => {
                    setNationalIdInput(e.target.value.replace(/\D/g, ""));
                    setLookupError(null);
                  }}
                  onKeyDown={(e) => e.key === "Enter" && handleLookupCitizen()}
                  className={`flex-1 border rounded-lg px-3 py-2.5 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] tracking-widest ${
                    lookupError ? "border-red-400 bg-red-50" : "border-[#DDE3EF]"
                  }`}
                  autoFocus
                />
                <button
                  type="button"
                  onClick={() => handleLookupCitizen()}
                  disabled={lookupLoading}
                  className="px-4 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center gap-1.5 disabled:opacity-50"
                >
                  {lookupLoading ? (
                    <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  ) : (
                    <SearchIcon className="w-3.5 h-3.5" />
                  )}
                  Search
                </button>
              </div>
              {lookupError && <p className="text-xs text-red-500 mt-1.5">{lookupError}</p>}

              <div className="mt-3 flex items-start gap-1.5 text-[11px] text-gray-400">
                <span className="shrink-0 mt-0.5">🔒</span>
                National ID is treated as sensitive information and will be masked after retrieval.
              </div>
              <div className="mt-4 bg-[#F4F6F9] rounded-lg px-4 py-3 text-xs text-gray-500">
                <span className="font-semibold text-gray-600">Demo NIDs: </span>
                <span
                  className="font-mono cursor-pointer hover:underline text-[#003087]"
                  onClick={() => setNationalIdInput("29805150101023")}
                >
                  29805150101023
                </span>{" "}
                ·{" "}
                <span
                  className="font-mono cursor-pointer hover:underline text-[#003087]"
                  onClick={() => setNationalIdInput("29901011234567")}
                >
                  29901011234567
                </span>
              </div>
            </div>
          )}

          {/* ── Step 2: Customer + Fee Selection ── */}
          {payStep === 2 && customerData && (
            <div className="space-y-4">
              {/* Customer card */}
              <div className="bg-[#F4F6F9] rounded-xl border border-[#E8EDF5] p-4 flex items-center gap-4">
                <div className="w-10 h-10 rounded-full bg-[#003087] text-white flex items-center justify-center font-bold text-sm shrink-0">
                  {((customerData.customer.name || customerData.customer.fullName || "Customer")
                    .trim()
                    .split(/\s+/)
                    .map((n) => n[0])
                    .join("")
                    .slice(0, 2)
                    .toUpperCase()) || "CU"}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="font-semibold text-[#1B2A4A] text-sm">
                    {customerData.customer.name || customerData.customer.fullName || "Customer"}
                  </div>
                  <div className="text-xs text-gray-400">
                    {customerData.customer.institution
                      ? `${customerData.customer.institution}${
                          customerData.customer.grade ? ` · ${customerData.customer.grade}` : ""
                        }`
                      : customerData.customer.cibCustomer
                      ? "Verified CIB Customer"
                      : "Educational Dues Account"}
                    {customerData.customer.mobileNumber && ` · ${customerData.customer.mobileNumber}`}
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-[10px] text-gray-400 font-semibold uppercase tracking-wider">
                    National ID
                  </div>
                  <div className="text-xs font-mono text-[#003087]">
                    {customerData.customer.nationalIdMasked ||
                      maskNID(customerData.customer.nationalId || nationalIdInput)}
                  </div>
                </div>
              </div>

              {/* Fee table */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <h4 className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                    Outstanding Fees
                  </h4>
                  <button
                    onClick={() => {
                      const allIds = customerData.fees.map((f) => f.id);
                      if (selectedFeeIds.length === allIds.length) {
                        setSelectedFeeIds([]);
                        setAmountInput("");
                      } else {
                        setSelectedFeeIds(allIds);
                        const sum = customerData.fees.reduce((acc, f) => acc + (f.remainingEGP ?? 0), 0);
                        setAmountInput(sum > 0 ? String(sum) : "");
                      }
                    }}
                    className="text-xs text-[#003087] font-semibold hover:underline"
                  >
                    {selectedFeeIds.length === customerData.fees.length ? "Deselect All" : "Select All"}
                  </button>
                </div>
                <div className="rounded-xl border border-[#E8EDF5] overflow-hidden">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-[#F8FAFD]">
                        <th className="w-10 px-4 py-2.5" />
                        {["Fee", "Original", "Remaining", "Status", "Due Date"].map((h) => (
                          <th
                            key={h}
                            className="text-left px-4 py-2.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider"
                          >
                            {h}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-50">
                      {customerData.fees.map((fee) => {
                        const isSelected = selectedFeeIds.includes(fee.id);
                        return (
                          <tr
                            key={fee.id}
                            onClick={() => toggleFeeSelection(fee.id)}
                            className={`cursor-pointer transition-colors ${
                              isSelected ? "bg-[#EBF1FB]" : "hover:bg-[#F8FAFD]"
                            }`}
                          >
                            <td className="px-4 py-3 text-center">
                              <input
                                type="checkbox"
                                checked={isSelected}
                                onChange={() => {}}
                                className="w-4 h-4 rounded border-gray-300 text-[#003087] cursor-pointer accent-[#003087]"
                              />
                            </td>
                            <td className="px-4 py-3 text-xs text-gray-700 font-medium">{fee.name}</td>
                            <td className="px-4 py-3 text-xs font-mono text-gray-600">
                              EGP {formatMoney(fee.originalAmountEGP)}
                            </td>
                            <td className="px-4 py-3 text-xs font-mono font-bold text-[var(--cib-blue)]">
                              EGP {formatMoney(fee.remainingEGP)}
                            </td>
                            <td className="px-4 py-3">
                              <span className="text-[11px] font-semibold px-2 py-0.5 rounded border bg-amber-50 text-amber-700 border-amber-200">
                                {fee.remainingEGP === fee.originalAmountEGP ? "Unpaid" : "Partial"}
                              </span>
                            </td>
                            <td className="px-4 py-3 text-xs font-mono text-gray-500">
                              {fee.dueDate ? formatIsoDate(fee.dueDate) : "—"}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Summary bar */}
              <div className="flex items-center justify-between bg-[#F4F6F9] rounded-xl border border-[#E8EDF5] px-5 py-3.5">
                <div className="text-sm">
                  <span className="text-gray-500">
                    {selectedFeeIds.length} fee{selectedFeeIds.length !== 1 ? "s" : ""} selected ·{" "}
                  </span>
                  <span className="font-semibold text-[#1B2A4A]">
                    Total: EGP {formatMoney(selectedFeesSum)}
                  </span>
                </div>
                <button
                  disabled={selectedFeeIds.length === 0}
                  onClick={() => {
                    setAmountInput(String(selectedFeesSum));
                    setPayStep(3);
                  }}
                  className="px-4 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors disabled:opacity-40"
                >
                  Continue to Payment →
                </button>
              </div>
            </div>
          )}

          {/* ── Step 3: Payment Amount ── */}
          {payStep === 3 && customerData && (
            <div className="max-w-lg mx-auto space-y-5 py-2">
              <div>
                <h3 className="text-base font-bold text-[#1B2A4A] mb-1">Payment Amount</h3>
                <p className="text-sm text-gray-400">
                  Confirm the amount to pay. Enter a lower amount to make a partial payment.
                </p>
              </div>

              <div className="rounded-xl border border-[#E8EDF5] overflow-hidden">
                {customerData.fees
                  .filter((f) => selectedFeeIds.includes(f.id))
                  .map((fee, i) => (
                    <div
                      key={fee.id}
                      className={`flex items-center justify-between px-4 py-3 text-xs ${
                        i > 0 ? "border-t border-gray-50" : ""
                      }`}
                    >
                      <span className="text-gray-700 font-medium">{fee.name}</span>
                      <span className="font-mono font-semibold text-gray-800">
                        EGP {formatMoney(fee.remainingEGP)}
                      </span>
                    </div>
                  ))}
                <div className="flex items-center justify-between px-4 py-3 border-t border-[#E8EDF5] bg-[#F8FAFD]">
                  <span className="text-xs font-semibold text-gray-600">Fee Balance</span>
                  <span className="text-sm font-bold font-mono text-[#1B2A4A]">
                    EGP {formatMoney(selectedFeesSum)}
                  </span>
                </div>
              </div>

              <div>
                <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">
                  Amount to Pay (EGP) *
                </label>
                <div className="flex gap-2">
                  <input
                    type="number"
                    min="1"
                    max={selectedFeesSum}
                    value={amountInput}
                    onChange={(e) => {
                      setAmountInput(e.target.value);
                      setAmountError(null);
                    }}
                    className={`flex-1 border rounded-lg px-3 py-2.5 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] ${
                      amountError ? "border-red-400 bg-red-50" : "border-[#DDE3EF]"
                    }`}
                    placeholder="Enter amount…"
                  />
                  <button
                    type="button"
                    onClick={() => {
                      setAmountInput(String(selectedFeesSum));
                      setAmountError(null);
                    }}
                    className="px-3 py-2.5 text-xs font-semibold border border-[#DDE3EF] rounded-lg hover:bg-gray-50 text-gray-600 transition-colors whitespace-nowrap"
                  >
                    Pay Full
                  </button>
                </div>
                {amountError && <p className="text-xs text-red-500 mt-1.5">{amountError}</p>}
              </div>

              {payAmountNum > 0 && (
                <div className="bg-[#F8FAFD] rounded-xl border border-[#E8EDF5] p-4 space-y-2.5">
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-500">Fee Balance</span>
                    <span className="font-mono text-gray-700">EGP {formatMoney(selectedFeesSum)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-gray-500">Amount Being Paid</span>
                    <span className="font-mono font-semibold text-[#003087]">
                      EGP {formatMoney(payAmountNum)}
                    </span>
                  </div>
                  <div
                    className={`flex justify-between text-sm border-t border-gray-100 pt-2.5 ${
                      payAmountNum > selectedFeesSum ? "text-red-600" : ""
                    }`}
                  >
                    <span className="font-semibold text-gray-700">Remaining After Payment</span>
                    <span className="font-mono font-bold">
                      {payAmountNum > selectedFeesSum
                        ? "—"
                        : `EGP ${formatMoney(selectedFeesSum - payAmountNum)}`}
                    </span>
                  </div>
                  {isPartial && (
                    <div className="flex items-center gap-1.5 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mt-1">
                      <ClockIcon className="w-3.5 h-3.5 shrink-0" />
                      Partial payment — EGP {formatMoney(selectedFeesSum - payAmountNum)} will remain as
                      outstanding balance.
                    </div>
                  )}
                </div>
              )}

              <div className="flex gap-3 pt-1">
                <button
                  onClick={() => setPayStep(2)}
                  className="px-4 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  ← Back
                </button>
                <button
                  onClick={() => {
                    if (!amountInput || payAmountNum <= 0) {
                      setAmountError("Enter a valid payment amount.");
                      return;
                    }
                    if (payAmountNum > selectedFeesSum) {
                      setAmountError(
                        `Amount cannot exceed the remaining balance of EGP ${formatMoney(selectedFeesSum)}.`
                      );
                      return;
                    }
                    setAmountError(null);
                    setPayStep(4);
                  }}
                  className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors"
                >
                  Continue to Review →
                </button>
              </div>
            </div>
          )}

          {/* ── Step 4: Review & Payment Method ── */}
          {payStep === 4 && customerData && (
            <div className="max-w-lg mx-auto space-y-5 py-2">
              <div>
                <h3 className="text-base font-bold text-[#1B2A4A] mb-1">Review & Confirm</h3>
                <p className="text-sm text-gray-400">Verify the details before processing the payment.</p>
              </div>

              <div className="rounded-xl border border-[#E8EDF5] overflow-hidden divide-y divide-gray-50">
                <div className="px-5 py-3 bg-[#F8FAFD] flex items-center justify-between">
                  <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    Customer
                  </span>
                  <span className="text-sm font-semibold text-[#1B2A4A]">
                    {customerData.customer.name || customerData.customer.fullName || "Customer"}
                  </span>
                </div>
                {customerData.fees
                  .filter((f) => selectedFeeIds.includes(f.id))
                  .map((fee) => (
                    <div key={fee.id} className="px-5 py-3 flex items-center justify-between">
                      <span className="text-xs text-gray-500">{fee.name}</span>
                      <span className="text-sm font-mono text-gray-700">
                        EGP {formatMoney(fee.remainingEGP)}
                      </span>
                    </div>
                  ))}
                <div className="px-5 py-3 flex items-center justify-between bg-[#F8FAFD]">
                  <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex items-center gap-1.5">
                    Amount to Pay
                    {isPartial && (
                      <span className="text-[10px] font-bold px-1.5 py-0.5 rounded bg-amber-100 text-amber-700 border border-amber-200">
                        Partial
                      </span>
                    )}
                  </span>
                  <span className="text-base font-bold font-mono text-[#003087]">
                    EGP {formatMoney(payAmountNum)}
                  </span>
                </div>
              </div>

              {/* Method choice */}
              <div className="space-y-2">
                <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider block">
                  Payment Method
                </label>
                <div className="grid grid-cols-2 gap-2">
                  {[
                    { id: "ACCOUNT_DEBIT", label: "CIB Account Debit" },
                    { id: "CARD", label: "CIB Card" },
                  ].map((m) => (
                    <button
                      key={m.id}
                      type="button"
                      onClick={() => {
                        setPayMethod(m.id as PayMethodChoice);
                        if (m.id !== "CARD") setIsEpp(false);
                      }}
                      className={`py-2.5 text-xs font-semibold rounded-lg border transition-all ${
                        payMethod === m.id
                          ? "border-[#003087] bg-[#EBF1FB] text-[#003087]"
                          : "border-[#DDE3EF] text-gray-500 hover:bg-gray-50"
                      }`}
                    >
                      {m.label}
                    </button>
                  ))}
                </div>

                {payMethod === "CARD" && (
                  <div className="rounded-xl border border-[#003087]/20 bg-[#F4F8FF] p-3 space-y-2.5">
                    <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                      Payment Type
                    </p>
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        onClick={() => setIsEpp(false)}
                        className={`py-2 text-xs font-semibold rounded-lg border transition-all ${
                          !isEpp
                            ? "border-[#003087] bg-[#EBF1FB] text-[#003087]"
                            : "border-[#DDE3EF] text-gray-500 bg-white hover:bg-gray-50"
                        }`}
                      >
                        Full Payment
                      </button>
                      <button
                        type="button"
                        onClick={() => setIsEpp(true)}
                        className={`py-2 text-xs font-semibold rounded-lg border transition-all ${
                          isEpp
                            ? "border-[#003087] bg-[#EBF1FB] text-[#003087]"
                            : "border-[#DDE3EF] text-gray-500 bg-white hover:bg-gray-50"
                        }`}
                      >
                        EPP / Installments
                      </button>
                    </div>

                    {isEpp && (
                      <div className="space-y-2 pt-1">
                        <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                          Installment Tenor
                        </p>
                        <div className="grid grid-cols-4 gap-1.5">
                          {[3, 6, 12, 18].map((t) => (
                            <button
                              key={t}
                              type="button"
                              onClick={() => setEppTenor(t)}
                              className={`py-2 text-xs font-semibold rounded-lg border transition-all ${
                                eppTenor === t
                                  ? "border-[#003087] bg-[#EBF1FB] text-[#003087]"
                                  : "border-[#DDE3EF] text-gray-500 bg-white hover:bg-gray-50"
                              }`}
                            >
                              {t}m
                            </button>
                          ))}
                        </div>
                        <div className="flex items-center justify-between pt-0.5">
                          <span className="text-[11px] text-gray-500">Est. monthly installment</span>
                          <span className="text-xs font-bold font-mono text-[#003087]">
                            EGP {formatMoney(Math.ceil(payAmountNum / eppTenor))} / month
                          </span>
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </div>

              {paymentError && (
                <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-xs flex items-center gap-2">
                  <AlertIcon className="w-4 h-4 shrink-0 text-red-500" />
                  <span>{paymentError}</span>
                </div>
              )}

              <div className="flex gap-3">
                <button
                  onClick={() => setPayStep(3)}
                  className="px-4 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  ← Back
                </button>
                <button
                  onClick={handleSubmitPayment}
                  disabled={paymentLoading}
                  className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center justify-center gap-2 disabled:opacity-50"
                >
                  <CreditCardIcon className="w-4 h-4" />
                  {paymentLoading ? "Processing Payment..." : "Process Payment"}
                </button>
              </div>
            </div>
          )}

          {/* ── Step 5: Receipt ── */}
          {payStep === 5 && customerData && (
            <div className="max-w-md mx-auto">
              <div className="bg-[#003087] text-white px-6 py-5 rounded-t-xl">
                <div className="text-[10px] uppercase tracking-widest opacity-50 mb-1">
                  Commercial International Bank Egypt
                </div>
                <div className="text-lg font-bold">Payment Receipt</div>
                <div className="text-xs opacity-60 mt-0.5">Official Transaction Record</div>
              </div>
              <div className="border border-t-0 border-[#E8EDF5] rounded-b-xl bg-white p-6 space-y-4 shadow-xs">
                <div className="flex items-center gap-2 bg-green-50 border border-green-200 rounded-lg px-4 py-2.5">
                  <CheckCircleIcon className="w-5 h-5 text-green-600 shrink-0" />
                  <span className="text-sm font-semibold text-green-800">Payment Successful</span>
                </div>

                <div className="space-y-3">
                  {[
                    [
                      "Transaction Ref.",
                      receiptRef || paymentSuccess?.receiptRef || paymentSuccess?.bankRef || "TX-CONFIRMED",
                    ],
                    [
                      "Date / Time",
                      receiptDate || "Just now",
                    ],
                    ["Customer", customerData.customer.name || customerData.customer.fullName || "Customer"],
                    [
                      "National ID",
                      customerData.customer.nationalIdMasked ||
                        maskNID(customerData.customer.nationalId || nationalIdInput),
                    ],
                    [
                      "Payment Method",
                      payMethod === "CARD" && isEpp
                        ? `CIB Card — EPP (${eppTenor}m)`
                        : payMethod === "CARD"
                        ? "CIB Card"
                        : "CIB Account Debit",
                    ],
                    ["Payment Status", "Successful"],
                  ].map(([k, v]) => (
                    <div key={k} className="flex justify-between text-xs">
                      <span className="text-gray-400 font-semibold">{k}</span>
                      <span
                        className={`font-semibold ${
                          k === "Transaction Ref."
                            ? "font-mono text-[#003087]"
                            : k === "Payment Status"
                            ? "text-green-700"
                            : "text-gray-700"
                        }`}
                      >
                        {v}
                      </span>
                    </div>
                  ))}
                </div>

                <div className="bg-[#F4F6F9] rounded-lg px-4 py-3 flex items-center justify-between border border-[#E8EDF5]">
                  <span className="text-sm text-gray-500">Amount Paid</span>
                  <span className="text-xl font-bold font-mono text-[#003087]">
                    EGP {formatMoney(paymentSuccess?.amountPaidEGP ?? payAmountNum)}
                  </span>
                </div>

                {isPartial && (
                  <div className="flex items-start gap-1.5 text-xs text-amber-700 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2.5">
                    <ClockIcon className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                    Partial payment — remaining balance of EGP{" "}
                    {formatMoney(selectedFeesSum - payAmountNum)} carried forward.
                  </div>
                )}

                <div className="flex gap-3 pt-2">
                  <button
                    type="button"
                    onClick={() => window.print()}
                    className="flex-1 py-2.5 text-xs font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors text-gray-500"
                  >
                    🖨 Print Receipt
                  </button>
                  <button
                    type="button"
                    onClick={resetPaymentWizard}
                    className="flex-1 py-2.5 text-xs font-semibold border border-[#003087]/30 rounded-lg text-[#003087] hover:bg-[#EBF1FB] transition-colors"
                  >
                    New Payment
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      resetPaymentWizard();
                      setMainView("list");
                    }}
                    className="flex-1 py-2.5 text-xs font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors"
                  >
                    Done
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    );
  }

  /* ─────────────────────────────────────────────────────────────
     VIEW 3: TRANSACTIONS LIST (Default)
  ───────────────────────────────────────────────────────────── */
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
            onClick={() => {
              resetPaymentWizard();
              setMainView("workflow");
            }}
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
            { id: "ALL", label: "All Transactions", count: tabCounts.all ?? tabCounts.All ?? 0 },
            { id: "SUCCESSFUL", label: "Successful", count: tabCounts.successful ?? tabCounts.Successful ?? 0 },
            { id: "PENDING", label: "Pending", count: tabCounts.pending ?? tabCounts.Pending ?? 0 },
            { id: "FAILED", label: "Failed", count: tabCounts.failed ?? tabCounts.Failed ?? 0 },
          ].map((tab) => {
            const isActive = activeTab === tab.id;
            const count = tab.count ?? 0;
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
                  {count.toLocaleString()}
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
              <option value="URGENT">Urgent (Due 1-6d)</option>
              <option value="HIGH">High (Due 7-14d)</option>
              <option value="MEDIUM">Medium</option>
              <option value="LOW">Low</option>
              <option value="PAID">Paid</option>
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

        {/* Clear Filters button */}
        {(search || institution || method || priority || dateFrom || dateTo) && (
          <div className="flex justify-end pt-1">
            <button
              onClick={handleResetFilters}
              className="text-xs text-red-600 hover:text-red-800 font-medium flex items-center gap-1 transition-colors"
            >
              <RefreshIcon className="w-3.5 h-3.5" />
              Reset Filters
            </button>
          </div>
        )}
      </div>

      {/* Main Table */}
      <div className="bg-white rounded-xl border shadow-xs overflow-hidden" style={{ borderColor: "#E8EDF5" }}>
        {loading ? (
          <div className="p-12 flex flex-col items-center justify-center space-y-3">
            <LoadingSpinner />
            <p className="text-xs text-gray-400">Loading transactions record...</p>
          </div>
        ) : transactions.length === 0 ? (
          <div className="p-12">
            <EmptyState
              title="No transactions found"
              description="There are no payment records matching your active filters or query."
            />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="bg-[#F8FAFD] border-b text-gray-500 font-semibold" style={{ borderColor: "#E8EDF5" }}>
                  <th className="py-3 px-4">REF / STUDENT</th>
                  <th className="py-3 px-4">INSTITUTION</th>
                  <th className="py-3 px-4">FEE TYPE</th>
                  <th className="py-3 px-4">AMOUNT</th>
                  <th className="py-3 px-4">METHOD</th>
                  <th className="py-3 px-4">DUE DATE &amp; PRIORITY</th>
                  <th className="py-3 px-4">TIMESTAMP</th>
                  <th className="py-3 px-4">STATUS</th>
                  <th className="py-3 px-4 text-right">ACTIONS</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {transactions.map((tx) => {
                  const pillStyle = STATUS_PILL_STYLES[tx.status] ?? {
                    bg: "bg-gray-50",
                    text: "text-gray-700",
                    border: "border-gray-200",
                  };
                  return (
                    <tr key={tx.id} className="hover:bg-[#F8FAFD] transition-colors">
                      {/* Ref & Student */}
                      <td className="py-3 px-4">
                        <div className="font-mono font-bold text-[var(--cib-blue)]">
                          TX-{tx.id.slice(0, 8).toUpperCase()}
                        </div>
                        <div className="text-gray-700 font-medium mt-0.5">{tx.student}</div>
                      </td>

                      {/* Institution */}
                      <td className="py-3 px-4 text-gray-600">
                        <div>{tx.institution}</div>
                        {tx.institutionType && (
                          <span className="text-[10px] uppercase font-bold text-gray-400">
                            {tx.institutionType}
                          </span>
                        )}
                      </td>

                      {/* Fee Type */}
                      <td className="py-3 px-4">
                        <span className="font-semibold text-gray-700">{tx.feeType}</span>
                      </td>

                      {/* Amount */}
                      <td className="py-3 px-4">
                        <div className="font-mono font-bold text-gray-900">
                          EGP {formatMoney(tx.amountEGP)}
                        </div>
                        {tx.partial?.isPartial && (
                          <div className="text-[10px] text-amber-700 font-medium">
                            Partial ({Math.round(((tx.amountEGP + tx.partial.previouslyPaidEGP) / (tx.partial.totalFeeAmountEGP || 1)) * 100)}%)
                          </div>
                        )}
                      </td>

                      {/* Method */}
                      <td className="py-3 px-4 text-gray-600">
                        {tx.method === "ACCOUNT_DEBIT" ? "Account Debit" : "CIB Card"}
                      </td>

                      {/* Due Date & Priority */}
                      <td className="py-3 px-4">
                        {tx.dueDate ? (
                          <div className="space-y-1">
                            <div className="text-gray-600 font-mono text-[11px]">{formatIsoDate(tx.dueDate)}</div>
                            {tx.daysToDue != null && (
                              <div
                                className={`text-[10px] font-medium ${
                                  tx.daysToDue < 0 ? "text-red-600" : tx.daysToDue === 0 ? "text-orange-600" : "text-gray-400"
                                }`}
                              >
                                {dueDateLabel(tx.daysToDue)}
                              </div>
                            )}
                          </div>
                        ) : (
                          <span className="text-gray-400">—</span>
                        )}
                      </td>

                      {/* Timestamp */}
                      <td className="py-3 px-4 text-gray-500 whitespace-nowrap">
                        {tx.timestamp}
                      </td>

                      {/* Status */}
                      <td className="py-3 px-4">
                        <span
                          className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${pillStyle.bg} ${pillStyle.text} ${pillStyle.border}`}
                        >
                          {tx.status}
                        </span>
                      </td>

                      {/* Actions */}
                      <td className="py-3 px-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => handleOpenDetail(tx)}
                            className="px-2.5 py-1 text-[11px] font-medium border rounded-md hover:bg-gray-50 text-gray-700 transition-colors"
                            style={{ borderColor: "#CBD5E1" }}
                          >
                            Details
                          </button>
                          {tx.status === "Failed" && (
                            <button
                              onClick={() => {
                                setRetryTarget(tx);
                                setRetryError(null);
                                setRetrySuccessMsg(null);
                              }}
                              className="px-2 py-1 text-[11px] font-medium rounded-md bg-rose-50 text-rose-700 border border-rose-200 hover:bg-rose-100 transition-colors"
                            >
                              Retry
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {/* Footer / Pagination */}
        <div className="border-t p-4 flex flex-col sm:flex-row items-center justify-between gap-4" style={{ borderColor: "#E8EDF5" }}>
          <div className="text-xs text-gray-500">
            Showing <span className="font-semibold text-gray-700">{transactions.length}</span> of{" "}
            <span className="font-semibold text-gray-700">{totalElements}</span> transactions
          </div>
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={(newPage) => setPage(newPage)}
          />
        </div>
      </div>

      {/* Retry Modal for Failed Transactions */}
      <Modal
        open={retryTarget !== null}
        onClose={() => setRetryTarget(null)}
        title="Retry Failed Transaction"
      >
        <div className="text-xs space-y-4">
          <p className="text-gray-600">
            Re-initiate authorization with the CIB Core Banking gateway for this transaction.
          </p>
          {retryTarget && (
            <div className="bg-gray-50 p-3 rounded-lg border border-gray-100 space-y-1.5 font-mono text-[11px]">
              <div className="flex justify-between">
                <span className="text-gray-500">Tx ID:</span>
                <span className="font-bold text-[var(--cib-blue)]">{retryTarget.id}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Student:</span>
                <span className="font-sans font-medium text-gray-800">{retryTarget.student}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Amount:</span>
                <span className="font-bold text-gray-900">EGP {formatMoney(retryTarget.amountEGP)}</span>
              </div>
            </div>
          )}
          {retryError && (
            <div className="p-3 bg-red-50 text-red-700 border border-red-200 rounded-lg">
              {retryError}
            </div>
          )}
          {retrySuccessMsg && (
            <div className="p-3 bg-green-50 text-green-700 border border-green-200 rounded-lg font-semibold">
              {retrySuccessMsg}
            </div>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" size="sm" onClick={() => setRetryTarget(null)} disabled={retryLoading}>
              Cancel
            </Button>
            <Button
              variant="primary"
              size="sm"
              onClick={handleRetryPayment}
              loading={retryLoading}
              disabled={retrySuccessMsg !== null}
            >
              Re-authorize Payment
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
