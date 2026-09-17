"use client";

import { useCallback, useEffect, useState } from "react";
import { useApiClient, useAuth, type PageResponse } from "@tuition/api-client";
import {
  Button,
  LoadingSpinner,
  Pagination,
  Modal,
  SearchIcon,
  EyeIcon,
  ChevronLeftIcon,
  CheckCircleIcon,
  ClockIcon,
  CalendarIcon,
  CreditCardIcon,
  PlusIcon,
  AlertIcon,
  XIcon,
  CheckIcon,
  formatIsoDate,
} from "@tuition/ui";
import type { TransactionDto } from "../transactions/types";
import type {
  EppPlanDetailDto,
  EppPlanListResponse,
  EppScheduleInstallmentDto,
  EppQuoteResponse,
  CardValidationResponse,
  CreateEppPlanRequest,
  EppSummaryResponse,
} from "./types";
import { statusClass, institutionTypePill, money, planRef, friendlyCreateError } from "./badges";

const TENORS = [3, 6, 12, 18] as const;

function CreateEppWizard({
  onClose,
  onCreated,
}: {
  onClose: () => void;
  onCreated: () => void;
}) {
  const apiClient = useApiClient();

  const [step, setStep] = useState<"payment" | "card" | "details" | "review" | "processing" | "success">("payment");

  const [paymentSearch, setPaymentSearch] = useState("");
  const [paymentResults, setPaymentResults] = useState<TransactionDto[]>([]);
  const [paymentSearchLoading, setPaymentSearchLoading] = useState(false);
  const [paymentSearchError, setPaymentSearchError] = useState<string | null>(null);
  const [selectedPayment, setSelectedPayment] = useState<TransactionDto | null>(null);

  const [cardNumber, setCardNumber] = useState("");
  const [cardChecked, setCardChecked] = useState(false);
  const [cardChecking, setCardChecking] = useState(false);
  const [cardResult, setCardResult] = useState<CardValidationResponse | null>(null);

  const [principal, setPrincipal] = useState("");
  const [tenor, setTenor] = useState<number>(12);
  const [quote, setQuote] = useState<EppQuoteResponse | null>(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [detailsError, setDetailsError] = useState("");

  const [createLoading, setCreateLoading] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [createdPlan, setCreatedPlan] = useState<EppPlanDetailDto | null>(null);

  const searchPayments = async () => {
    setPaymentSearchLoading(true);
    setPaymentSearchError(null);
    try {
      const params = new URLSearchParams();
      params.set("method", "CREDIT_CARD");
      params.set("status", "Successful");
      params.set("page", "0");
      params.set("pageSize", "10");
      if (paymentSearch.trim()) params.set("search", paymentSearch.trim());
      const res = await apiClient.get<PageResponse<TransactionDto>>(`/transactions?${params.toString()}`);
      setPaymentResults(res.data ?? []);
    } catch (err) {
      setPaymentSearchError(err instanceof Error ? err.message : "Failed to search payments.");
    } finally {
      setPaymentSearchLoading(false);
    }
  };

  useEffect(() => {
    Promise.resolve().then(() => searchPayments());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const selectPayment = (tx: TransactionDto) => {
    setSelectedPayment(tx);
    setStep("card");
  };

  const checkCard = async () => {
    setCardChecking(true);
    try {
      const res = await apiClient.post<CardValidationResponse>("/epp/cards/validate", { cardNumber });
      setCardResult(res);
      setCardChecked(true);
    } catch (err) {
      setCardResult({
        eligible: false,
        bank: null,
        cardType: null,
        maxTenor: null,
        reason: err instanceof Error ? err.message : "Card validation failed.",
        result: "rejected-not-eligible",
      });
      setCardChecked(true);
    } finally {
      setCardChecking(false);
    }
  };

  const proceedToDetails = () => {
    if (!cardResult?.eligible) return;
    setPrincipal(selectedPayment ? String(Math.round(selectedPayment.amountEGP)) : "");
    setStep("details");
  };

  useEffect(() => {
    if (step !== "details") return;
    const p = parseFloat(principal);
    let ignore = false;
    if (!p || p <= 0) {
      const clearTimer = setTimeout(() => !ignore && setQuote(null), 0);
      return () => {
        ignore = true;
        clearTimeout(clearTimer);
      };
    }
    const t = setTimeout(() => {
      setQuoteLoading(true);
      apiClient
        .post<EppQuoteResponse>("/epp/quote", { principalEGP: p, tenor })
        .then((res) => !ignore && setQuote(res))
        .catch(() => !ignore && setQuote(null))
        .finally(() => !ignore && setQuoteLoading(false));
    }, 350);
    return () => {
      ignore = true;
      clearTimeout(t);
    };
  }, [apiClient, principal, tenor, step]);

  const proceedToReview = () => {
    const p = parseFloat(principal);
    if (!p || p <= 0) {
      setDetailsError("Enter a valid principal amount.");
      return;
    }
    if (p < 5000 || p > 100000) {
      setDetailsError("Principal must be between EGP 5,000 and EGP 100,000.");
      return;
    }
    setDetailsError("");
    setStep("review");
  };

  const handleCreate = async () => {
    if (!selectedPayment) return;
    setStep("processing");
    setCreateLoading(true);
    setCreateError(null);
    try {
      const payload: CreateEppPlanRequest = {
        cardToken: cardNumber.replace(/\s/g, ""),
        studentName: selectedPayment.student,
        institution: selectedPayment.institution,
        feeDescription: selectedPayment.feeType,
        principalEGP: parseFloat(principal),
        tenor,
        sourcePaymentId: selectedPayment.id,
      };
      const created = await apiClient.post<EppPlanDetailDto>("/epp/plans", payload);
      setCreatedPlan(created);
      setStep("success");
    } catch (err) {
      setCreateError(err instanceof Error ? friendlyCreateError(err.message) : "Failed to create EPP plan.");
      setStep("review");
    } finally {
      setCreateLoading(false);
    }
  };

  const stepLabel: Record<string, string> = {
    payment: "Step 1 of 4 — Find Payment",
    card: "Step 2 of 4 — Card Validation",
    details: "Step 3 of 4 — Plan Details",
    review: "Step 4 of 4 — Review & Confirm",
    processing: "Step 4 of 4 — Review & Confirm",
    success: "Plan Created",
  };

  const stepOrder = ["payment", "card", "details", "review"];

  return (
    <div role="dialog" aria-modal="true" className="fixed inset-0 bg-black/40 flex items-end sm:items-center justify-center z-50 p-4">
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#E8EDF5]">
          <div>
            <h2 className="text-base font-bold text-[#1B2A4A]">Create EPP Plan</h2>
            <p className="text-[11px] text-gray-400 mt-0.5">{stepLabel[step]}</p>
          </div>
          <button onClick={onClose} className="p-1.5 rounded hover:bg-gray-100 text-gray-400 transition-colors">
            <XIcon className="w-4 h-4" />
          </button>
        </div>

        {step !== "success" && step !== "processing" && (
          <div className="flex gap-1 px-6 pt-4">
            {stepOrder.map((s, i) => (
              <div
                key={s}
                className={`flex-1 h-1 rounded-full transition-all ${
                  step === s ? "bg-[#F7941D]" : i < stepOrder.indexOf(step) ? "bg-[#003087]" : "bg-gray-100"
                }`}
              />
            ))}
          </div>
        )}

        <div className="px-6 py-5 space-y-4">
          {step === "payment" && (
            <>
              <div>
                <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                  Payment Reference or Student
                </label>
                <div className="flex gap-2">
                  <div className="relative flex-1">
                    <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
                    <input
                      type="text"
                      value={paymentSearch}
                      onChange={(e) => setPaymentSearch(e.target.value)}
                      onKeyDown={(e) => e.key === "Enter" && searchPayments()}
                      placeholder="Search by reference or student…"
                      className="w-full pl-8 pr-3 py-2.5 border border-[#DDE3EF] rounded-lg text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                    />
                  </div>
                  <button
                    onClick={searchPayments}
                    className="px-4 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors"
                  >
                    Search
                  </button>
                </div>
                <p className="text-[11px] text-gray-400 mt-1.5">
                  Only successful CIB credit-card payments are eligible for EPP conversion.
                </p>
              </div>

              {paymentSearchError && (
                <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-xs flex items-center gap-2">
                  <AlertIcon className="w-4 h-4 shrink-0 text-red-500" />
                  <span>{paymentSearchError}</span>
                </div>
              )}

              {paymentSearchLoading ? (
                <div className="py-8 flex justify-center">
                  <LoadingSpinner />
                </div>
              ) : paymentResults.length === 0 ? (
                <div className="py-8 text-center text-sm text-gray-400">No eligible credit-card payments found.</div>
              ) : (
                <div className="rounded-xl border border-[#E8EDF5] divide-y divide-gray-50 max-h-72 overflow-y-auto">
                  {paymentResults.map((tx) => (
                    <button
                      key={tx.id}
                      onClick={() => selectPayment(tx)}
                      className="w-full text-left px-4 py-3 hover:bg-[#F8FAFD] transition-colors flex items-center justify-between gap-3"
                    >
                      <div className="min-w-0">
                        <div className="text-sm font-semibold text-gray-800 truncate">{tx.student}</div>
                        <div className="text-xs text-gray-400 truncate">
                          {tx.institution} · {tx.feeType}
                        </div>
                        <div className="text-[11px] font-mono text-gray-400 mt-0.5">
                          {tx.bankRef || tx.id.slice(0, 8)}
                        </div>
                      </div>
                      <div className="text-right shrink-0">
                        <div className="text-sm font-mono font-bold text-[#003087]">
                          EGP {money(tx.amountEGP)}
                        </div>
                        <div className="text-[10px] text-gray-400">{tx.timestamp?.slice(0, 10)}</div>
                      </div>
                    </button>
                  ))}
                </div>
              )}
            </>
          )}

          {step === "card" && (
            <>
              {selectedPayment && (
                <div className="bg-[#F8FAFD] rounded-xl border border-[#E8EDF5] px-4 py-3 flex items-center justify-between">
                  <div>
                    <div className="text-sm font-semibold text-gray-800">{selectedPayment.student}</div>
                    <div className="text-xs text-gray-400">{selectedPayment.institution}</div>
                  </div>
                  <div className="text-sm font-mono font-bold text-[#003087]">
                    EGP {money(selectedPayment.amountEGP)}
                  </div>
                </div>
              )}
              <div>
                <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                  Card Number
                </label>
                <input
                  type="text"
                  value={cardNumber}
                  onChange={(e) => {
                    const digits = e.target.value.replace(/\D/g, "").slice(0, 16);
                    setCardNumber(digits.replace(/(.{4})/g, "$1 ").trim());
                    setCardChecked(false);
                    setCardResult(null);
                  }}
                  placeholder="XXXX XXXX XXXX XXXX"
                  maxLength={19}
                  className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm font-mono text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                />
                <p className="text-[11px] text-gray-400 mt-1">
                  Demo: any 16-digit number is treated as a CIB credit card, except numbers starting with{" "}
                  <span className="font-mono text-red-500">5078 / 5888 / 6703 / 4000 00 / 4023 / 5000</span> which are
                  classified as debit and rejected.
                </p>
              </div>

              {cardChecked && cardResult?.eligible && (
                <div className="flex items-center gap-2 px-4 py-3 bg-green-50 border border-green-200 rounded-xl text-sm text-green-700 font-medium">
                  <CheckCircleIcon className="w-4 h-4 shrink-0" /> {cardResult.bank} {cardResult.cardType} card
                  verified — eligible for EPP
                </div>
              )}
              {cardChecked && cardResult && !cardResult.eligible && (
                <div className="flex items-start gap-2 px-4 py-3 bg-red-50 border border-red-200 rounded-xl">
                  <AlertIcon className="w-4 h-4 text-red-500 mt-0.5 shrink-0" />
                  <div>
                    <p className="text-sm font-semibold text-red-700">Not eligible</p>
                    <p className="text-xs text-red-600 mt-0.5">{cardResult.reason}</p>
                  </div>
                </div>
              )}

              <div className="flex gap-3 pt-2">
                <button
                  onClick={() => setStep("payment")}
                  className="px-4 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  ← Back
                </button>
                {!cardChecked && (
                  <button
                    onClick={checkCard}
                    disabled={cardNumber.replace(/\s/g, "").length < 16 || cardChecking}
                    className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors disabled:opacity-40"
                  >
                    {cardChecking ? "Validating…" : "Validate Card"}
                  </button>
                )}
                {cardChecked && cardResult?.eligible && (
                  <button
                    onClick={proceedToDetails}
                    className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors"
                  >
                    Continue →
                  </button>
                )}
                {cardChecked && cardResult && !cardResult.eligible && (
                  <button
                    onClick={() => {
                      setCardChecked(false);
                      setCardResult(null);
                      setCardNumber("");
                    }}
                    className="flex-1 py-2.5 text-sm font-semibold border border-[#003087] text-[#003087] rounded-lg hover:bg-[#EBF1FB] transition-colors"
                  >
                    Try Different Card
                  </button>
                )}
              </div>
            </>
          )}

          {step === "details" && (
            <>
              {detailsError && (
                <div className="flex items-center gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
                  <AlertIcon className="w-3.5 h-3.5 shrink-0" />
                  {detailsError}
                </div>
              )}
              <div>
                <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                  Principal (EGP) <span className="text-red-400">*</span>
                </label>
                <input
                  type="number"
                  value={principal}
                  onChange={(e) => setPrincipal(e.target.value)}
                  placeholder="0.00"
                  min="5000"
                  max="100000"
                  className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm font-mono text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                />
                <p className="text-[11px] text-gray-400 mt-1">Must be between EGP 5,000 and EGP 100,000.</p>
              </div>
              <div>
                <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                  Tenor
                </label>
                <div className="grid grid-cols-4 gap-1">
                  {TENORS.map((t) => (
                    <button
                      key={t}
                      onClick={() => setTenor(t)}
                      className={`py-2 text-xs font-semibold rounded-lg border transition-all ${
                        tenor === t ? "border-[#003087] bg-[#EBF1FB] text-[#003087]" : "border-[#DDE3EF] text-gray-500 hover:bg-gray-50"
                      }`}
                    >
                      {t}m
                    </button>
                  ))}
                </div>
              </div>

              {quoteLoading && (
                <div className="flex justify-center py-2">
                  <LoadingSpinner />
                </div>
              )}
              {!quoteLoading && quote && (
                <div className="bg-[#F8FAFD] rounded-xl p-4 space-y-2 text-xs">
                  <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2">Plan Preview</p>
                  {[
                    ["Principal", `EGP ${money(quote.principalEGP)}`],
                    [`Interest (${quote.interestRatePct}% p.a.)`, `EGP ${money(quote.interestEGP)}`],
                    ["Admin Fee", `EGP ${money(quote.adminFeeEGP)}`],
                  ].map(([l, v]) => (
                    <div key={l} className="flex justify-between text-gray-500">
                      <span>{l}</span>
                      <span className="font-mono">{v}</span>
                    </div>
                  ))}
                  <div className="flex justify-between font-bold text-gray-800 border-t border-gray-100 pt-2">
                    <span>Total Payable</span>
                    <span className="font-mono">EGP {money(quote.totalEGP)}</span>
                  </div>
                  <div className="flex justify-between font-bold text-[#F7941D]">
                    <span>Monthly Installment</span>
                    <span className="font-mono">EGP {money(quote.monthlyEGP)}</span>
                  </div>
                </div>
              )}

              <div className="flex gap-3 pt-2">
                <button
                  onClick={() => setStep("card")}
                  className="px-4 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  ← Back
                </button>
                <button
                  onClick={proceedToReview}
                  className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors"
                >
                  Review Plan →
                </button>
              </div>
            </>
          )}

          {step === "review" && (
            <>
              {createError && (
                <div className="flex items-center gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
                  <AlertIcon className="w-3.5 h-3.5 shrink-0" />
                  {createError}
                </div>
              )}
              <div className="bg-[#F8FAFD] rounded-xl p-5 space-y-3">
                <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-3">Plan Summary</p>
                {[
                  ["Student", selectedPayment?.student ?? "—"],
                  ["Institution", selectedPayment?.institution ?? "—"],
                  ["Fee", selectedPayment?.feeType ?? "—"],
                  ["Card", `•••• •••• •••• ${cardNumber.replace(/\s/g, "").slice(-4)} (${cardResult?.bank ?? "CIB"} ${cardResult?.cardType ?? "Credit"})`],
                  ["Principal", `EGP ${money(parseFloat(principal))}`],
                  ["Tenor", `${tenor} months`],
                ].map(([l, v]) => (
                  <div key={l} className="flex justify-between text-sm">
                    <span className="text-gray-500">{l}</span>
                    <span className="font-medium text-gray-800">{v}</span>
                  </div>
                ))}
                {quote && (
                  <>
                    <div className="border-t border-[#DDE3EF] pt-3 flex justify-between font-bold text-base">
                      <span className="text-gray-800">Total Payable</span>
                      <span className="font-mono text-[#1B2A4A]">EGP {money(quote.totalEGP)}</span>
                    </div>
                    <div className="flex justify-between text-sm font-bold text-[#F7941D]">
                      <span>Monthly Installment</span>
                      <span className="font-mono">EGP {money(quote.monthlyEGP)}</span>
                    </div>
                  </>
                )}
              </div>
              <div className="flex gap-3 pt-2">
                <button
                  onClick={() => setStep("details")}
                  className="px-4 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  ← Back
                </button>
                <button
                  onClick={handleCreate}
                  disabled={createLoading}
                  className="flex-1 py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center justify-center gap-2 disabled:opacity-50"
                >
                  <CreditCardIcon className="w-4 h-4" /> Create EPP Plan
                </button>
              </div>
            </>
          )}

          {step === "processing" && (
            <div className="py-10 text-center space-y-4">
              <div className="w-14 h-14 rounded-full border-4 border-[#003087]/20 border-t-[#003087] animate-spin mx-auto" />
              <div>
                <p className="font-semibold text-gray-800">Creating EPP Plan…</p>
                <p className="text-sm text-gray-400 mt-1">Please wait while we set up the installment plan.</p>
              </div>
            </div>
          )}

          {step === "success" && createdPlan && (
            <div className="space-y-4">
              <div className="py-6 text-center space-y-3">
                <div className="w-14 h-14 rounded-full bg-green-100 flex items-center justify-center mx-auto">
                  <CheckCircleIcon className="w-7 h-7 text-green-600" />
                </div>
                <div>
                  <p className="font-bold text-gray-800 text-base">EPP Plan Created</p>
                  <p className="text-xs font-mono text-[#003087] mt-1">{planRef(createdPlan.id)}</p>
                </div>
              </div>
              <div className="bg-[#F8FAFD] rounded-xl p-4 space-y-2.5 text-sm">
                {[
                  ["Student", createdPlan.student ?? "—"],
                  ["Institution", createdPlan.institution ?? "—"],
                  ["Principal", `EGP ${money(createdPlan.principalEGP)}`],
                  ["Tenor", `${createdPlan.tenor} months`],
                  ["Monthly Installment", `EGP ${money(createdPlan.monthlyEGP)}`],
                  ["Total Payable", `EGP ${money(createdPlan.totalEGP)}`],
                  ["Status", createdPlan.status],
                  ["First Payment", createdPlan.firstPaymentDate ? formatIsoDate(createdPlan.firstPaymentDate) : "—"],
                ].map(([l, v]) => (
                  <div key={l} className="flex justify-between">
                    <span className="text-gray-400">{l}</span>
                    <span className="font-medium text-gray-800 font-mono text-xs">{v}</span>
                  </div>
                ))}
              </div>
              <button
                onClick={onCreated}
                className="w-full py-2.5 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center justify-center gap-2"
              >
                <CheckIcon className="w-4 h-4" /> Done — View EPP Plans
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function EppPlanDetail({
  planId,
  canManage,
  onBack,
  onChanged,
}: {
  planId: string;
  canManage: boolean;
  onBack: () => void;
  onChanged: () => void;
}) {
  const apiClient = useApiClient();
  const [plan, setPlan] = useState<EppPlanDetailDto | null>(null);
  const [schedule, setSchedule] = useState<EppScheduleInstallmentDto[]>([]);
  const [loading, setLoading] = useState(true);

  const [showCancel, setShowCancel] = useState(false);
  const [cancelReason, setCancelReason] = useState("");
  const [cancelLoading, setCancelLoading] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  useEffect(() => {
    let ignore = false;
    Promise.all([
      apiClient.get<EppPlanDetailDto>(`/epp/plans/${planId}`),
      apiClient.get<EppScheduleInstallmentDto[]>(`/epp/plans/${planId}/schedule`),
    ])
      .then(([planRes, scheduleRes]) => {
        if (ignore) return;
        setPlan(planRes);
        setSchedule(scheduleRes ?? []);
      })
      .finally(() => !ignore && setLoading(false));
    return () => {
      ignore = true;
    };
  }, [apiClient, planId]);

  const handleCancelPlan = async () => {
    setCancelLoading(true);
    setCancelError(null);
    try {
      const updated = await apiClient.patch<EppPlanDetailDto>(`/epp/plans/${planId}`, {
        status: "CANCELLED",
        reason: cancelReason || undefined,
      });
      setPlan(updated);
      setShowCancel(false);
      onChanged();
    } catch (err) {
      setCancelError(err instanceof Error ? err.message : "Failed to cancel plan.");
    } finally {
      setCancelLoading(false);
    }
  };

  if (loading || !plan) {
    return (
      <div className="flex flex-col items-center justify-center py-24 space-y-3">
        <LoadingSpinner />
        <p className="text-sm text-gray-500">Loading EPP plan...</p>
      </div>
    );
  }

  const nextDue = schedule.find((s) => s.status !== "Paid");

  return (
    <div className="space-y-4">
      <button
        onClick={onBack}
        className="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-700 transition-colors"
      >
        <ChevronLeftIcon className="w-4 h-4" /> Back to EPP Plans
      </button>

      <div className="grid grid-cols-3 gap-4">
        <div className="col-span-2 bg-white rounded-xl border border-[#E8EDF5] p-6">
          <div className="flex items-start justify-between mb-5 pb-5 border-b border-gray-100">
            <div>
              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">EPP Plan</div>
              <h2 className="text-lg font-bold text-[#1B2A4A] font-mono">{planRef(plan.id)}</h2>
              <div className="flex items-center gap-2 mt-0.5">
                <p className="text-xs text-gray-400">
                  {plan.payRef} · {plan.institution || "—"}
                </p>
                <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${institutionTypePill(plan.institutionType)}`}>
                  {plan.institutionType || "—"}
                </span>
              </div>
            </div>
            <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${statusClass(plan.status)}`}>
              {plan.status}
            </span>
          </div>

          <div className="grid grid-cols-3 gap-6">
            {[
              ["Student", plan.student || "—"],
              ["Institution", plan.institution || "—"],
              ["Start Date", plan.startDate ? formatIsoDate(plan.startDate) : "—"],
            ].map(([k, v]) => (
              <div key={k}>
                <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">{k}</div>
                <div className="text-sm text-gray-800">{v}</div>
              </div>
            ))}
          </div>

          <div className="mt-6 bg-[#F8FAFD] rounded-xl p-5">
            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-4">Pricing Breakdown</h3>
            <div className="space-y-2.5">
              {[
                ["Principal", `${money(plan.principalEGP)} EGP`],
                ["Tenor", `${plan.tenor} months`],
                [`Interest (${plan.interestRatePct}% per annum)`, `${money(plan.interestEGP)} EGP`],
                ["Admin Fee", `${money(plan.adminFeeEGP)} EGP`],
              ].map(([label, value]) => (
                <div key={label} className="flex items-center justify-between text-sm">
                  <span className="text-gray-500">{label}</span>
                  <span className="font-mono font-semibold text-gray-700">{value}</span>
                </div>
              ))}
              <div className="border-t border-[#DDE3EF] pt-2.5 flex items-center justify-between">
                <span className="text-sm font-bold text-[#1B2A4A]">Total Payable</span>
                <span className="font-mono font-bold text-[#1B2A4A] text-base">{money(plan.totalEGP)} EGP</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-sm text-gray-500">Monthly Installment</span>
                <span className="font-mono font-bold text-[#F7941D] text-lg">{money(plan.monthlyEGP)} EGP</span>
              </div>
            </div>
          </div>

          <div className="mt-4 space-y-3">
            <div className="flex items-center gap-3">
              <div className="flex-1">
                <div className="flex items-center justify-between text-xs mb-1.5">
                  <span className="text-gray-500">
                    {plan.progress.paidInstallments} of {plan.progress.totalInstallments} installments paid
                  </span>
                  <span className="font-semibold text-gray-700">{Math.round(plan.progress.percent)}%</span>
                </div>
                <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-[#003087] rounded-full transition-all"
                    style={{ width: `${plan.progress.percent}%` }}
                  />
                </div>
              </div>
              <span className="text-xs text-gray-400 whitespace-nowrap">
                {plan.progress.totalInstallments - plan.progress.paidInstallments} remaining
              </span>
            </div>
            <div className="flex items-center gap-3">
              <div className="flex-1">
                <div className="flex items-center justify-between text-xs mb-1.5">
                  <span className="text-gray-500">
                    EGP {money(plan.paidInstallments * plan.monthlyEGP)} paid of EGP {money(plan.totalEGP)}
                  </span>
                  <span className="font-semibold text-gray-700">
                    {plan.totalEGP ? Math.round(((plan.paidInstallments * plan.monthlyEGP) / plan.totalEGP) * 100) : 0}%
                  </span>
                </div>
                <div className="h-2 bg-gray-100 rounded-full overflow-hidden">
                  <div
                    className="h-full bg-green-500 rounded-full transition-all"
                    style={{ width: `${plan.totalEGP ? ((plan.paidInstallments * plan.monthlyEGP) / plan.totalEGP) * 100 : 0}%` }}
                  />
                </div>
              </div>
              <span className="text-xs text-gray-400 whitespace-nowrap">
                EGP {money((plan.tenor - plan.paidInstallments) * plan.monthlyEGP)} left
              </span>
            </div>
          </div>

          {canManage && plan.status === "Active" && (
            <div className="mt-5 pt-4 border-t border-gray-100">
              <button
                onClick={() => setShowCancel(true)}
                className="text-xs font-semibold text-red-600 border border-red-200 rounded-lg px-3 py-2 hover:bg-red-50 transition-colors"
              >
                Cancel Plan
              </button>
            </div>
          )}
        </div>

        <div className="bg-white rounded-xl border border-[#E8EDF5] p-5">
          <h3 className="text-sm font-semibold text-[#1B2A4A] mb-4">Plan Summary</h3>
          <div className="space-y-4">
            {[
              { label: "Total Paid", value: `${money(plan.paidInstallments * plan.monthlyEGP)} EGP`, color: "text-green-600" },
              {
                label: "Outstanding",
                value: `${money((plan.tenor - plan.paidInstallments) * plan.monthlyEGP)} EGP`,
                color: "text-[#003087]",
              },
              { label: "Next Due Date", value: nextDue ? formatIsoDate(nextDue.dueDate) : "—", color: "text-gray-700" },
              { label: "Payment Reference", value: plan.payRef ?? "—", color: "text-[#003087] font-mono text-[11px]" },
            ].map(({ label, value, color }) => (
              <div key={label} className="bg-gray-50 rounded-lg p-3">
                <div className="text-[11px] text-gray-400 uppercase tracking-wider font-semibold mb-1">{label}</div>
                <div className={`text-sm font-bold ${color}`}>{value}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
        <div className="px-5 py-4 border-b border-gray-100">
          <h3 className="text-sm font-semibold text-[#1B2A4A]">Installment Schedule</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-[#F8FAFD]">
                {["#", "Due Date", "Principal (EGP)", "Interest (EGP)", "Amount (EGP)", "Paid Amount", "Status"].map((h) => (
                  <th key={h} className="text-left px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {schedule.map((row) => (
                <tr key={row.number} className={`transition-colors ${row.status === "Due" ? "bg-amber-50/50" : "hover:bg-[#F8FAFD]"}`}>
                  <td className="px-5 py-3 text-xs font-mono font-semibold text-gray-500">{row.number}</td>
                  <td className="px-5 py-3 text-xs text-gray-600">{formatIsoDate(row.dueDate)}</td>
                  <td className="px-5 py-3 text-xs font-mono text-gray-700">{row.principalEGP != null ? money(row.principalEGP) : "—"}</td>
                  <td className="px-5 py-3 text-xs font-mono text-gray-700">{row.interestEGP != null ? money(row.interestEGP) : "—"}</td>
                  <td className="px-5 py-3 text-xs font-mono font-bold text-gray-800">{money(row.amountEGP)}</td>
                  <td className="px-5 py-3 text-xs font-mono">
                    {row.status === "Paid" ? (
                      <span className="text-green-700 font-semibold">{money(row.paidAmountEGP)}</span>
                    ) : (
                      <span className="text-gray-300">—</span>
                    )}
                  </td>
                  <td className="px-5 py-3">
                    <span
                      className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${
                        row.status === "Paid"
                          ? "bg-green-50 text-green-700 border-green-200"
                          : row.status === "Due"
                          ? "bg-amber-50 text-amber-700 border-amber-200"
                          : "bg-gray-50 text-gray-400 border-gray-200"
                      }`}
                    >
                      {row.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <Modal
        open={showCancel}
        onClose={() => setShowCancel(false)}
        title="Cancel EPP Plan"
        footer={
          <>
            <Button variant="secondary" onClick={() => setShowCancel(false)} disabled={cancelLoading}>
              Back
            </Button>
            <Button variant="danger" onClick={handleCancelPlan} disabled={cancelLoading}>
              {cancelLoading ? "Cancelling…" : "Cancel Plan"}
            </Button>
          </>
        }
      >
        <p className="text-sm text-gray-600 mb-3">
          This stops future installments on {planRef(plan.id)}. This cannot be undone from this screen.
        </p>
        <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
          Reason (optional)
        </label>
        <textarea
          value={cancelReason}
          onChange={(e) => setCancelReason(e.target.value)}
          rows={3}
          placeholder="Why is this plan being cancelled?"
          className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] resize-none"
        />
        {cancelError && <p className="text-xs text-red-500 mt-2">{cancelError}</p>}
      </Modal>
    </div>
  );
}

export default function EppPlansPage() {
  const apiClient = useApiClient();
  const { user } = useAuth();
  const canCreate = user?.role === "bank-admin" || user?.role === "bank-operations" || user?.role === "bank-finance";

  const [summary, setSummary] = useState<EppSummaryResponse | null>(null);
  const [plans, setPlans] = useState<EppPlanListResponse | null>(null);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState("");
  const [filterStatus, setFilterStatus] = useState("All");
  const [filterTenor, setFilterTenor] = useState("All");
  const [loading, setLoading] = useState(true);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);

  const loadAll = useCallback(() => {
    const params = new URLSearchParams();
    params.set("page", String(page));
    params.set("pageSize", "10");
    if (search.trim()) params.set("search", search.trim());
    if (filterStatus !== "All") params.set("status", filterStatus);
    if (filterTenor !== "All") params.set("tenor", filterTenor);

    Promise.all([
      apiClient.get<EppSummaryResponse>("/epp/summary"),
      apiClient.get<EppPlanListResponse>(`/epp/plans?${params.toString()}`),
    ])
      .then(([summaryRes, plansRes]) => {
        setSummary(summaryRes);
        setPlans(plansRes);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [apiClient, page, search, filterStatus, filterTenor]);

  useEffect(() => {
    loadAll();
  }, [loadAll, refreshTrigger]);

  if (selectedPlanId) {
    return (
      <EppPlanDetail
        key={selectedPlanId}
        planId={selectedPlanId}
        canManage={canCreate}
        onBack={() => setSelectedPlanId(null)}
        onChanged={() => setRefreshTrigger((p) => p + 1)}
      />
    );
  }

  const kpis = [
    {
      label: "Active Plans",
      value: (summary?.active ?? 0).toLocaleString(),
      Icon: CreditCardIcon,
      color: "bg-[#003087]/10 text-[#003087]",
    },
    {
      label: "Completed",
      value: (summary?.completed ?? 0).toLocaleString(),
      Icon: CheckCircleIcon,
      color: "bg-green-50 text-green-600",
    },
    {
      label: "Defaulted",
      value: (summary?.defaulted ?? 0).toLocaleString(),
      Icon: ClockIcon,
      color: "bg-red-50 text-red-500",
    },
    {
      label: "Total Outstanding",
      value: `EGP ${((summary?.totalOutstandingEGP ?? 0) / 1000).toFixed(0)}K`,
      Icon: CalendarIcon,
      color: "bg-[#F7941D]/15 text-[#C96B10]",
    },
  ];

  return (
    <div className="space-y-4 pb-12">
      {showCreate && (
        <CreateEppWizard
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            setRefreshTrigger((p) => p + 1);
          }}
        />
      )}

      <div>
        <h1 className="text-xl font-bold tracking-tight" style={{ color: "var(--cib-blue)" }}>
          EPP Plans
        </h1>
        <p className="text-xs text-gray-500 mt-1">
          Extended Payment Plan installment schedules converted from successful CIB credit-card payments.
        </p>
      </div>

      {loading && !plans ? (
        <div className="flex flex-col items-center justify-center py-24 space-y-3">
          <LoadingSpinner />
          <p className="text-sm text-gray-500">Loading EPP plans...</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-4 gap-4">
            {kpis.map(({ label, value, Icon, color }) => (
              <div key={label} className="bg-white rounded-xl border border-[#E8EDF5] p-5 flex items-center gap-4">
                <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${color} shrink-0`}>
                  <Icon className="w-5 h-5" />
                </div>
                <div>
                  <div className="text-xs font-semibold text-gray-400 uppercase tracking-wider">{label}</div>
                  <div className="text-xl font-bold text-[#1B2A4A]">{value}</div>
                </div>
              </div>
            ))}
          </div>

          <div className="flex items-center gap-3">
            <div className="relative flex-1 max-w-xs">
              <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
              <input
                type="text"
                placeholder="Plan ID, student or reference…"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(1);
                }}
                className="w-full pl-8 pr-4 py-2 border border-[#DDE3EF] rounded-lg text-sm placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] bg-white"
              />
            </div>
            <select
              value={filterStatus}
              onChange={(e) => {
                setFilterStatus(e.target.value);
                setPage(1);
              }}
              className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white"
            >
              <option value="All">All Statuses</option>
              <option>Active</option>
              <option>Completed</option>
              <option>Defaulted</option>
              <option>Cancelled</option>
            </select>
            <select
              value={filterTenor}
              onChange={(e) => {
                setFilterTenor(e.target.value);
                setPage(1);
              }}
              className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white"
            >
              <option value="All">All Tenors</option>
              {TENORS.map((t) => (
                <option key={t} value={t}>
                  {t} Months
                </option>
              ))}
            </select>
            {canCreate && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => setShowCreate(true)}
                className="ml-auto flex items-center gap-2"
              >
                <PlusIcon className="w-3.5 h-3.5" /> Create EPP Plan
              </Button>
            )}
          </div>

          <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[#F8FAFD] border-b border-[#E8EDF5]">
                    {[
                      "Plan ID",
                      "Payment Ref",
                      "Institution",
                      "Type",
                      "Student",
                      "Principal",
                      "Tenor",
                      "Total Payable",
                      "Monthly",
                      "Progress",
                      "Status",
                      "",
                    ].map((h) => (
                      <th key={h} className="text-left px-4 py-3.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {(plans?.data ?? []).length === 0 && (
                    <tr>
                      <td colSpan={12} className="px-4 py-8 text-center text-sm text-gray-400">
                        No EPP plans match these filters.
                      </td>
                    </tr>
                  )}
                  {(plans?.data ?? []).map((plan) => (
                    <tr key={plan.id} className="hover:bg-[#F8FAFD] transition-colors">
                      <td className="px-4 py-3.5 font-mono text-xs font-bold text-[#003087] whitespace-nowrap">
                        {planRef(plan.id)}
                      </td>
                      <td className="px-4 py-3.5 font-mono text-xs text-gray-500 whitespace-nowrap">{plan.payRef ?? "—"}</td>
                      <td className="px-4 py-3.5 text-xs text-gray-700 max-w-[120px] truncate">{plan.institution || "—"}</td>
                      <td className="px-4 py-3.5 whitespace-nowrap">
                        <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${institutionTypePill(plan.institutionType)}`}>
                          {plan.institutionType || "—"}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-xs text-gray-600 whitespace-nowrap">{plan.student || "—"}</td>
                      <td className="px-4 py-3.5 text-xs font-mono font-bold text-gray-800 whitespace-nowrap">
                        {money(plan.principalEGP)} EGP
                      </td>
                      <td className="px-4 py-3.5 text-xs text-center">
                        <span className="bg-[#003087]/10 text-[#003087] font-bold px-2 py-0.5 rounded text-[11px]">
                          {plan.tenor}m
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-xs font-mono font-semibold text-gray-700 whitespace-nowrap">
                        {money(plan.totalEGP)} EGP
                      </td>
                      <td className="px-4 py-3.5 text-xs font-mono font-bold text-[#F7941D] whitespace-nowrap">
                        {money(plan.monthlyEGP)} EGP
                      </td>
                      <td className="px-4 py-3.5 w-24">
                        <div className="flex items-center gap-1.5">
                          <div className="flex-1 h-1.5 bg-gray-100 rounded-full overflow-hidden">
                            <div
                              className="h-full bg-[#003087] rounded-full"
                              style={{ width: `${plan.tenor ? (plan.paidInstallments / plan.tenor) * 100 : 0}%` }}
                            />
                          </div>
                          <span className="text-[10px] text-gray-400 whitespace-nowrap font-mono">
                            {plan.paidInstallments}/{plan.tenor}
                          </span>
                        </div>
                      </td>
                      <td className="px-4 py-3.5">
                        <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${statusClass(plan.status)}`}>
                          {plan.status}
                        </span>
                      </td>
                      <td className="px-4 py-3.5">
                        <button
                          onClick={() => setSelectedPlanId(plan.id)}
                          className="p-1.5 rounded hover:bg-[#003087]/10 text-[#003087] transition-colors"
                        >
                          <EyeIcon className="w-3.5 h-3.5" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between px-5 py-3.5 border-t border-gray-100">
              <span className="text-xs text-gray-400">
                Showing {plans?.data.length ?? 0} of {plans?.total ?? 0} plans
              </span>
              <Pagination page={page} totalPages={Math.max(1, plans?.totalPages ?? 1)} onPageChange={setPage} />
            </div>
          </div>
        </>
      )}
    </div>
  );
}
