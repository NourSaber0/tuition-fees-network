"use client";

import React, { useState, useEffect } from "react";
import { useApiClient } from "@tuition/api-client";
import { Badge, Button, LoadingSpinner } from "@tuition/ui";
import type {
  StudentSearchItem,
  StudentFeeItem,
  StudentFeesResponse,
  SchoolPosPaymentResponse,
} from "./types";

interface SchoolPosModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export function SchoolPosModal({ isOpen, onClose, onSuccess }: SchoolPosModalProps) {
  const client = useApiClient();

  // Stepper state: 1 = Student & Fees, 2 = Amount & Penalty, 3 = Terminal Tap, 4 = Receipt
  const [step, setStep] = useState<1 | 2 | 3 | 4>(1);

  // Student search
  const [searchQuery, setSearchQuery] = useState("");
  const [searching, setSearching] = useState(false);
  const [searchResults, setSearchResults] = useState<StudentSearchItem[]>([]);
  const [selectedStudent, setSelectedStudent] = useState<StudentSearchItem | null>(null);

  // Fees loading & selection
  const [feesLoading, setFeesLoading] = useState(false);
  const [fees, setFees] = useState<StudentFeeItem[]>([]);
  const [selectedFeeIds, setSelectedFeeIds] = useState<string[]>([]);

  // Amount & Penalty calculation
  const [amountMode, setAmountMode] = useState<"principal" | "total" | "custom">("total");
  const [customAmount, setCustomAmount] = useState<string>("");

  // POS Terminal simulation
  const posTerminalId = "POS-SCH-01";
  const [posState, setPosState] = useState<"idle" | "reading" | "approved">("idle");
  const [posAuthRef, setPosAuthRef] = useState<string>("");

  // Processing & Receipt
  const [processing, setProcessing] = useState(false);
  const [paymentError, setPaymentError] = useState<string | null>(null);
  const [receipt, setReceipt] = useState<SchoolPosPaymentResponse | null>(null);

  // Reset when modal opens/closes
  useEffect(() => {
    if (isOpen) {
      setStep(1);
      setSearchQuery("");
      setSearchResults([]);
      setSelectedStudent(null);
      setFees([]);
      setSelectedFeeIds([]);
      setAmountMode("total");
      setCustomAmount("");
      setPosState("idle");
      setPosAuthRef("");
      setProcessing(false);
      setPaymentError(null);
      setReceipt(null);
    }
  }, [isOpen]);

  // Live search for students
  useEffect(() => {
    if (!searchQuery || searchQuery.trim().length < 2) {
      setSearchResults([]);
      return;
    }
    const timer = setTimeout(async () => {
      setSearching(true);
      try {
        const res = await client.get<StudentSearchItem[]>(
          `/students/search?q=${encodeURIComponent(searchQuery.trim())}`
        );
        setSearchResults(res || []);
      } catch (err) {
        console.error("Failed to search students:", err);
      } finally {
        setSearching(false);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [searchQuery, client]);

  // Load student fees when student selected
  const handleSelectStudent = async (student: StudentSearchItem) => {
    setSelectedStudent(student);
    setSearchQuery("");
    setSearchResults([]);
    setFeesLoading(true);
    try {
      const res = await client.get<StudentFeesResponse>(`/students/${student.id}/fees`);
      const unpaid = (res?.data || []).filter(
        (f) => f.status !== "PAID" && f.remainingEGP > 0
      );
      setFees(unpaid);
      // Pre-select all unpaid fees by default
      setSelectedFeeIds(unpaid.map((f) => f.feeId));
    } catch (err) {
      console.error("Failed to load fees for student:", err);
      setFees([]);
    } finally {
      setFeesLoading(false);
    }
  };

  // Fee toggle handler
  const handleToggleFee = (feeId: string) => {
    setSelectedFeeIds((prev) =>
      prev.includes(feeId) ? prev.filter((id) => id !== feeId) : [...prev, feeId]
    );
  };

  // Calculations for selected fees
  const selectedFees = fees.filter((f) => selectedFeeIds.includes(f.feeId));

  const todayStr = new Date().toISOString().slice(0, 10);
  const selectedPrincipalSum = selectedFees.reduce((acc, f) => acc + (f.remainingEGP || 0), 0);

  // Late penalty is 5% for overdue fees
  const selectedPenaltySum = selectedFees.reduce((acc, f) => {
    const isOverdue = f.status === "OVERDUE" || (f.dueDate && f.dueDate < todayStr);
    return isOverdue ? acc + Math.round(f.remainingEGP * 0.05) : acc;
  }, 0);

  const totalDueSum = selectedPrincipalSum + selectedPenaltySum;

  const effectiveAmount =
    amountMode === "principal"
      ? selectedPrincipalSum
      : amountMode === "total"
      ? totalDueSum
      : Number(customAmount) || 0;

  // POS Card Reader Tap Simulation
  const handleSimulateCardTap = () => {
    setPosState("reading");
    setPaymentError(null);
    setTimeout(() => {
      const code = `AUTH-POS-SCH-${Math.floor(100000 + Math.random() * 900000)}`;
      setPosAuthRef(code);
      setPosState("approved");
    }, 1200);
  };

  // Submit payment
  const handleProcessPayment = async () => {
    if (selectedFeeIds.length === 0) {
      setPaymentError("Please select at least one fee to pay.");
      return;
    }
    if (effectiveAmount <= 0) {
      setPaymentError("Payment amount must be greater than zero.");
      return;
    }
    if (posState !== "approved" || !posAuthRef) {
      setPaymentError("Please tap/swipe the card on the POS terminal first.");
      return;
    }

    setProcessing(true);
    setPaymentError(null);

    try {
      const payload = {
        feeIds: selectedFeeIds,
        amountEGP: effectiveAmount,
        method: "POS Terminal",
        sourceId: posTerminalId,
        posTerminalId: posTerminalId,
        posAuthRef: posAuthRef,
        channel: "School POS",
        processedBy: "School Counter Cashier",
      };

      const idempKey = window.crypto.randomUUID();
      const res = await client.post<SchoolPosPaymentResponse>("/payments", payload, {
        headers: { "Idempotency-Key": idempKey },
      });
      setReceipt(res);
      setStep(4);
    } catch (err: any) {
      setPaymentError(
        err?.message || "Failed to process school POS payment. Please verify terminal connection."
      );
    } finally {
      setProcessing(false);
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-xs">
      <div className="bg-white rounded-2xl shadow-2xl border border-gray-100 w-full max-w-2xl max-h-[92vh] flex flex-col overflow-hidden">
        {/* Header */}
        <div className="px-6 py-4 bg-gradient-to-r from-orange-600 via-amber-600 to-orange-700 text-white flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-white/20 flex items-center justify-center backdrop-blur-xs">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                className="h-6 w-6 text-white"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={2}
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"
                />
              </svg>
            </div>
            <div>
              <h2 className="text-lg font-bold">Collect Payment (School POS Terminal)</h2>
              <p className="text-xs text-orange-100">
                School Cashier Counter &bull; Terminal <span className="font-mono font-semibold">{posTerminalId}</span>
              </p>
            </div>
          </div>
          {step !== 4 && (
            <button
              onClick={onClose}
              className="p-2 text-white/80 hover:text-white hover:bg-white/10 rounded-lg transition-colors"
            >
              ✕
            </button>
          )}
        </div>

        {/* Stepper tracker */}
        {step !== 4 && (
          <div className="flex items-center justify-between px-6 py-3 bg-gray-50 border-b border-gray-100 text-xs font-medium text-gray-500">
            <div className={`flex items-center gap-2 ${step >= 1 ? "text-orange-600 font-bold" : ""}`}>
              <span className={`w-5 h-5 rounded-full flex items-center justify-center ${step >= 1 ? "bg-orange-600 text-white" : "bg-gray-200"}`}>
                1
              </span>
              <span>Select Student &amp; Fees</span>
            </div>
            <div className="w-8 h-[1px] bg-gray-300" />
            <div className={`flex items-center gap-2 ${step >= 2 ? "text-orange-600 font-bold" : ""}`}>
              <span className={`w-5 h-5 rounded-full flex items-center justify-center ${step >= 2 ? "bg-orange-600 text-white" : "bg-gray-200"}`}>
                2
              </span>
              <span>Amount &amp; Penalty</span>
            </div>
            <div className="w-8 h-[1px] bg-gray-300" />
            <div className={`flex items-center gap-2 ${step >= 3 ? "text-orange-600 font-bold" : ""}`}>
              <span className={`w-5 h-5 rounded-full flex items-center justify-center ${step >= 3 ? "bg-orange-600 text-white" : "bg-gray-200"}`}>
                3
              </span>
              <span>POS Terminal Tap</span>
            </div>
          </div>
        )}

        {/* Content body */}
        <div className="p-6 overflow-y-auto flex-1 space-y-5">
          {paymentError && (
            <div className="p-3 bg-red-50 border border-red-200 text-red-700 text-sm rounded-xl flex items-center gap-2">
              <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 text-red-500 flex-shrink-0" viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z" clipRule="evenodd" />
              </svg>
              <span>{paymentError}</span>
            </div>
          )}

          {/* ------------------------------------------------------------- */}
          {/* STEP 1: Student Search & Fee Selection                       */}
          {/* ------------------------------------------------------------- */}
          {step === 1 && (
            <div className="space-y-4">
              {!selectedStudent ? (
                <div>
                  <label className="block text-sm font-semibold text-gray-800 mb-1.5">
                    Search Student <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <input
                      type="text"
                      placeholder="Type student name or student code (e.g., Omar, Sara)..."
                      className="w-full pl-10 pr-4 py-2.5 text-sm border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-orange-500 bg-white"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                      autoFocus
                    />
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      className="h-4 w-4 text-gray-400 absolute left-3.5 top-1/2 -translate-y-1/2"
                      fill="none"
                      viewBox="0 0 24 24"
                      stroke="currentColor"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                    </svg>
                    {searching && (
                      <div className="absolute right-3.5 top-1/2 -translate-y-1/2">
                        <LoadingSpinner size={16} />
                      </div>
                    )}
                  </div>

                  {/* Search results dropdown */}
                  {searchResults.length > 0 && (
                    <div className="mt-2 border border-gray-200 rounded-xl bg-white shadow-lg overflow-hidden divide-y divide-gray-100 max-h-56 overflow-y-auto">
                      {searchResults.map((stu) => (
                        <div
                          key={stu.id}
                          onClick={() => handleSelectStudent(stu)}
                          className="p-3 hover:bg-orange-50 cursor-pointer flex items-center justify-between transition-colors"
                        >
                          <div>
                            <div className="text-sm font-semibold text-gray-900">{stu.name}</div>
                            <div className="text-xs text-gray-500">
                              Ref: <span className="font-mono">{stu.studentRef}</span> &bull; Grade: {stu.grade} ({stu.section})
                            </div>
                          </div>
                          <Badge tone="info">{stu.status || "Active"}</Badge>
                        </div>
                      ))}
                    </div>
                  )}
                  {searchQuery.trim().length >= 2 && !searching && searchResults.length === 0 && (
                    <p className="text-xs text-gray-500 mt-2">No students found matching &quot;{searchQuery}&quot;.</p>
                  )}
                </div>
              ) : (
                /* Selected Student Banner */
                <div className="p-3.5 bg-orange-50 border border-orange-200 rounded-xl flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-lg bg-orange-500 text-white flex items-center justify-center font-bold text-sm">
                      {selectedStudent.name.charAt(0)}
                    </div>
                    <div>
                      <div className="text-sm font-bold text-gray-900">{selectedStudent.name}</div>
                      <div className="text-xs text-gray-600">
                        ID: <span className="font-mono">{selectedStudent.studentRef}</span> &bull; Grade: {selectedStudent.grade}
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={() => {
                      setSelectedStudent(null);
                      setFees([]);
                      setSelectedFeeIds([]);
                    }}
                    className="text-xs font-semibold text-orange-700 hover:text-orange-900 hover:underline px-2 py-1"
                  >
                    Change Student
                  </button>
                </div>
              )}

              {/* Outstanding Fees List */}
              {selectedStudent && (
                <div className="space-y-3 pt-2">
                  <div className="flex items-center justify-between">
                    <h3 className="text-sm font-bold text-gray-800">Outstanding Fees ({fees.length})</h3>
                    {fees.length > 0 && (
                      <button
                        onClick={() =>
                          setSelectedFeeIds(
                            selectedFeeIds.length === fees.length ? [] : fees.map((f) => f.feeId)
                          )
                        }
                        className="text-xs text-orange-600 hover:underline font-medium"
                      >
                        {selectedFeeIds.length === fees.length ? "Deselect All" : "Select All"}
                      </button>
                    )}
                  </div>

                  {feesLoading ? (
                    <div className="flex justify-center py-8">
                      <LoadingSpinner size={24} />
                    </div>
                  ) : fees.length === 0 ? (
                    <div className="p-6 text-center bg-gray-50 rounded-xl border border-dashed border-gray-200">
                      <p className="text-sm text-gray-600 font-medium">No outstanding fees for this student!</p>
                      <p className="text-xs text-gray-400 mt-1">All invoices are settled.</p>
                    </div>
                  ) : (
                    <div className="space-y-2 max-h-60 overflow-y-auto pr-1">
                      {fees.map((fee) => {
                        const isOverdue = fee.status === "OVERDUE" || (fee.dueDate && fee.dueDate < todayStr);
                        const penalty = isOverdue ? Math.round(fee.remainingEGP * 0.05) : 0;
                        const isChecked = selectedFeeIds.includes(fee.feeId);

                        return (
                          <div
                            key={fee.feeId}
                            onClick={() => handleToggleFee(fee.feeId)}
                            className={`p-3 rounded-xl border cursor-pointer transition-all flex items-center justify-between ${
                              isChecked
                                ? "border-orange-500 bg-orange-50/40 shadow-xs"
                                : "border-gray-200 hover:border-gray-300 bg-white"
                            }`}
                          >
                            <div className="flex items-center gap-3">
                              <input
                                type="checkbox"
                                checked={isChecked}
                                onChange={() => handleToggleFee(fee.feeId)}
                                className="w-4 h-4 rounded text-orange-600 focus:ring-orange-500 border-gray-300 pointer-events-none"
                              />
                              <div>
                                <div className="text-sm font-semibold text-gray-900">{fee.name}</div>
                                <div className="text-xs text-gray-500 flex items-center gap-2 mt-0.5">
                                  <span>{fee.category}</span>
                                  <span>&bull;</span>
                                  <span>Due: {fee.dueDate}</span>
                                  {isOverdue && (
                                    <span className="px-1.5 py-0.2 rounded text-[10px] font-bold bg-red-100 text-red-700 border border-red-200">
                                      OVERDUE (+5% penalty)
                                    </span>
                                  )}
                                </div>
                              </div>
                            </div>
                            <div className="text-right">
                              <div className="text-sm font-bold text-gray-900">
                                EGP {fee.remainingEGP.toLocaleString()}
                              </div>
                              {penalty > 0 && (
                                <div className="text-[11px] font-medium text-amber-600">
                                  + EGP {penalty.toLocaleString()} penalty
                                </div>
                              )}
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}

                  {/* Summary footer */}
                  {selectedFeeIds.length > 0 && (
                    <div className="mt-4 p-3 bg-gray-50 border border-gray-200 rounded-xl flex items-center justify-between text-xs">
                      <div>
                        <span className="text-gray-500">Selected Fees Principal: </span>
                        <span className="font-semibold text-gray-800">EGP {selectedPrincipalSum.toLocaleString()}</span>
                        {selectedPenaltySum > 0 && (
                          <>
                            <span className="text-gray-400 mx-1.5">|</span>
                            <span className="text-amber-600 font-semibold">Late Penalty: + EGP {selectedPenaltySum.toLocaleString()}</span>
                          </>
                        )}
                      </div>
                      <div className="text-sm font-bold text-gray-900">
                        Total Due: EGP {totalDueSum.toLocaleString()}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* ------------------------------------------------------------- */}
          {/* STEP 2: Amount & Penalty Settlement Choice                   */}
          {/* ------------------------------------------------------------- */}
          {step === 2 && (
            <div className="space-y-5">
              <div>
                <h3 className="text-sm font-bold text-gray-800 mb-1">Choose Payment Collection Amount</h3>
                <p className="text-xs text-gray-500">
                  Select whether to collect the full amount including late penalties, or waive/defer the penalty.
                </p>
              </div>

              <div className="space-y-3">
                {/* Option 1: Total with Penalty (Full) */}
                <div
                  onClick={() => setAmountMode("total")}
                  className={`p-4 rounded-xl border cursor-pointer transition-all ${
                    amountMode === "total"
                      ? "border-emerald-500 bg-emerald-50/40 ring-1 ring-emerald-500"
                      : "border-gray-200 hover:border-gray-300 bg-white"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className={`w-4 h-4 rounded-full border flex items-center justify-center ${amountMode === "total" ? "border-emerald-600" : "border-gray-400"}`}>
                        {amountMode === "total" && <div className="w-2 h-2 rounded-full bg-emerald-600" />}
                      </div>
                      <div>
                        <div className="text-sm font-bold text-gray-900">
                          Total with Penalty (Full Settlement)
                        </div>
                        <div className="text-xs text-gray-500 mt-0.5">
                          Principal (EGP {selectedPrincipalSum.toLocaleString()}) + Late Penalty (EGP {selectedPenaltySum.toLocaleString()})
                        </div>
                      </div>
                    </div>
                    <div className="text-base font-extrabold text-emerald-700">
                      EGP {totalDueSum.toLocaleString()}
                    </div>
                  </div>
                  {amountMode === "total" && selectedPenaltySum > 0 && (
                    <div className="mt-2.5 pt-2 border-t border-emerald-200 text-xs text-emerald-700 flex items-center gap-1.5 font-medium">
                      <span>✓</span> Late penalty of EGP {selectedPenaltySum.toLocaleString()} will be marked as <strong>Late Penalty (Paid)</strong>.
                    </div>
                  )}
                </div>

                {/* Option 2: Principal Only */}
                <div
                  onClick={() => setAmountMode("principal")}
                  className={`p-4 rounded-xl border cursor-pointer transition-all ${
                    amountMode === "principal"
                      ? "border-amber-500 bg-amber-50/40 ring-1 ring-amber-500"
                      : "border-gray-200 hover:border-gray-300 bg-white"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className={`w-4 h-4 rounded-full border flex items-center justify-center ${amountMode === "principal" ? "border-amber-600" : "border-gray-400"}`}>
                        {amountMode === "principal" && <div className="w-2 h-2 rounded-full bg-amber-600" />}
                      </div>
                      <div>
                        <div className="text-sm font-bold text-gray-900">
                          Principal Tuition Only (Penalty Deferred)
                        </div>
                        <div className="text-xs text-gray-500 mt-0.5">
                          Collect strictly the core tuition without charging the late penalty.
                        </div>
                      </div>
                    </div>
                    <div className="text-base font-extrabold text-amber-800">
                      EGP {selectedPrincipalSum.toLocaleString()}
                    </div>
                  </div>
                  {amountMode === "principal" && selectedPenaltySum > 0 && (
                    <div className="mt-2.5 pt-2 border-t border-amber-200 text-xs text-amber-800 flex items-center gap-1.5 font-medium">
                      <span>⚠️</span> The EGP {selectedPenaltySum.toLocaleString()} penalty will NOT be collected and will remain marked as <strong>Late Penalty (Unpaid)</strong> on the student record.
                    </div>
                  )}
                </div>

                {/* Option 3: Custom Partial Amount */}
                <div
                  onClick={() => setAmountMode("custom")}
                  className={`p-4 rounded-xl border cursor-pointer transition-all ${
                    amountMode === "custom"
                      ? "border-blue-500 bg-blue-50/40 ring-1 ring-blue-500"
                      : "border-gray-200 hover:border-gray-300 bg-white"
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <div className={`w-4 h-4 rounded-full border flex items-center justify-center ${amountMode === "custom" ? "border-blue-600" : "border-gray-400"}`}>
                        {amountMode === "custom" && <div className="w-2 h-2 rounded-full bg-blue-600" />}
                      </div>
                      <div>
                        <div className="text-sm font-bold text-gray-900">Custom Partial Amount</div>
                        <div className="text-xs text-gray-500 mt-0.5">Enter custom amount to collect today</div>
                      </div>
                    </div>
                  </div>
                  {amountMode === "custom" && (
                    <div className="mt-3 pt-3 border-t border-blue-200 flex items-center gap-2">
                      <span className="text-sm font-semibold text-gray-700">EGP</span>
                      <input
                        type="number"
                        placeholder="Enter amount..."
                        value={customAmount}
                        onChange={(e) => setCustomAmount(e.target.value)}
                        className="w-full px-3 py-2 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white"
                        autoFocus
                      />
                    </div>
                  )}
                </div>
              </div>

              {/* Summary of effective charge */}
              <div className="p-4 bg-orange-50/70 border border-orange-200 rounded-xl flex items-center justify-between">
                <div>
                  <div className="text-xs text-gray-500">POS Charge Summary</div>
                  <div className="text-sm font-bold text-gray-900">
                    {amountMode === "principal" ? "Principal Only" : amountMode === "total" ? "Full Settlement" : "Custom Payment"}
                  </div>
                </div>
                <div className="text-xl font-extrabold text-orange-700">
                  EGP {effectiveAmount.toLocaleString()}
                </div>
              </div>
            </div>
          )}

          {/* ------------------------------------------------------------- */}
          {/* STEP 3: Counter POS Terminal Tap & Authorization             */}
          {/* ------------------------------------------------------------- */}
          {step === 3 && (
            <div className="space-y-5">
              {/* POS Hardware Card Mockup */}
              <div className="p-5 bg-gradient-to-br from-gray-900 via-slate-800 to-gray-900 rounded-2xl text-white shadow-xl border border-gray-700">
                <div className="flex items-center justify-between border-b border-gray-700/80 pb-3.5 mb-4">
                  <div className="flex items-center gap-2.5">
                    <div className="w-3 h-3 rounded-full bg-emerald-400 animate-pulse" />
                    <span className="font-mono text-xs text-gray-300 tracking-wider font-semibold">
                      TERMINAL: {posTerminalId}
                    </span>
                  </div>
                  <span className="text-xs bg-emerald-500/20 text-emerald-300 px-2.5 py-0.5 rounded-full font-mono border border-emerald-500/30">
                    CIB SCHOOL NETWORK
                  </span>
                </div>

                <div className="text-center py-4 space-y-2">
                  <div className="text-xs text-gray-400 font-medium">COLLECTING AMOUNT</div>
                  <div className="text-3xl font-extrabold tracking-tight text-white">
                    EGP {effectiveAmount.toLocaleString()}
                  </div>
                  <div className="text-xs text-gray-300">
                    Student: <span className="font-semibold text-white">{selectedStudent?.name}</span> ({selectedStudent?.studentRef})
                  </div>
                </div>

                {/* Reader state display */}
                <div className="mt-4 p-4 rounded-xl bg-gray-800/80 border border-gray-700 flex flex-col items-center justify-center text-center">
                  {posState === "idle" && (
                    <div className="space-y-3">
                      <div className="w-12 h-12 rounded-full bg-orange-500/20 text-orange-400 flex items-center justify-center mx-auto">
                        <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 11c0 3.517-1.009 6.799-2.753 9.571m-3.44-2.04l.054-.09A13.916 13.916 0 008 11a4 4 0 118 0c0 1.017-.07 2.019-.203 3m-2.118 6.844A21.88 21.88 0 0015.171 17m3.839 1.132c.645-2.266.99-4.659.99-7.132A8 8 0 004 11m0 0a8 8 0 001.077 4m6.823 4.888A12.01 12.01 0 0112 21" />
                        </svg>
                      </div>
                      <div>
                        <div className="text-sm font-bold text-gray-200">Terminal Ready for Card</div>
                        <div className="text-xs text-gray-400 mt-0.5">
                          Ask parent/guardian to tap or insert any Visa, Mastercard, or Meeza card.
                        </div>
                      </div>
                      <Button
                        variant="primary"
                        size="sm"
                        onClick={handleSimulateCardTap}
                        className="bg-orange-500 hover:bg-orange-600 text-white font-medium px-4 py-2"
                      >
                        Simulate Card Tap / Chip Insertion
                      </Button>
                    </div>
                  )}

                  {posState === "reading" && (
                    <div className="space-y-2 py-2">
                      <LoadingSpinner size={28} />
                      <div className="text-sm font-bold text-amber-300">Reading EMV Chip / NFC Tap...</div>
                      <div className="text-xs text-gray-400">Verifying PIN with Bank Gateway...</div>
                    </div>
                  )}

                  {posState === "approved" && (
                    <div className="space-y-2 py-1">
                      <div className="w-10 h-10 rounded-full bg-emerald-500 text-white flex items-center justify-center mx-auto shadow-md">
                        ✓
                      </div>
                      <div className="text-sm font-bold text-emerald-400">Card Approved &amp; Authorized</div>
                      <div className="text-xs font-mono text-gray-300">
                        Auth Ref: <span className="text-white font-bold">{posAuthRef}</span>
                      </div>
                      <div className="text-[11px] text-gray-400">
                        Mode: Chip &amp; PIN Contactless &bull; Channel: School POS
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* Confirmation card */}
              {posState === "approved" && (
                <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-emerald-900 flex items-center justify-between">
                  <div>
                    <div className="font-bold text-sm">Ready to Collect &amp; Issue Receipt</div>
                    <div>Click below to record payment on the student ledger and print receipt.</div>
                  </div>
                  <Button
                    variant="primary"
                    size="md"
                    onClick={handleProcessPayment}
                    disabled={processing}
                    className="bg-emerald-600 hover:bg-emerald-700 text-white font-bold shadow-md"
                  >
                    {processing ? "Submitting Payment..." : `Confirm & Collect EGP ${effectiveAmount.toLocaleString()}`}
                  </Button>
                </div>
              )}
            </div>
          )}

          {/* ------------------------------------------------------------- */}
          {/* STEP 4: Official Digital Receipt                             */}
          {/* ------------------------------------------------------------- */}
          {step === 4 && receipt && (
            <div className="space-y-6">
              <div className="text-center space-y-1">
                <div className="w-14 h-14 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center text-2xl mx-auto shadow-sm">
                  ✓
                </div>
                <h3 className="text-lg font-bold text-gray-900">Payment Collected Successfully!</h3>
                <p className="text-xs text-gray-500">Official School Counter POS Receipt</p>
              </div>

              {/* Printable Receipt Paper Card */}
              <div className="p-5 bg-gray-50/80 border border-gray-200 rounded-2xl space-y-4 font-mono text-xs text-gray-700 shadow-xs">
                <div className="text-center border-b border-dashed border-gray-300 pb-3">
                  <div className="font-bold text-sm text-gray-900 tracking-wider">CIB TUITION NETWORK</div>
                  <div className="text-[11px] text-gray-500">SCHOOL COUNTER POS TERMINAL</div>
                  <div className="text-[10px] text-gray-400 mt-0.5">Terminal: {posTerminalId} &bull; Channel: School POS</div>
                </div>

                <div className="space-y-2 border-b border-dashed border-gray-300 pb-3">
                  <div className="flex justify-between">
                    <span className="text-gray-500">Receipt Ref:</span>
                    <span className="font-bold text-gray-900">{receipt.receiptRef || "RCP-POS"}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-500">Transaction ID:</span>
                    <span className="font-bold text-gray-900">{receipt.transactionId}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-500">Bank Auth Code:</span>
                    <span className="font-bold text-gray-900">{receipt.authCode || posAuthRef}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-500">Student:</span>
                    <span className="font-bold text-gray-900">{selectedStudent?.name}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-500">Student Ref:</span>
                    <span className="font-bold text-gray-900">{selectedStudent?.studentRef}</span>
                  </div>
                </div>

                <div className="space-y-2 border-b border-dashed border-gray-300 pb-3">
                  <div className="flex justify-between text-sm font-bold text-gray-900">
                    <span>Amount Paid:</span>
                    <span className="text-emerald-700">EGP {receipt.amountPaidEGP.toLocaleString()}</span>
                  </div>

                  {/* Penalty Breakdown */}
                  {receipt.penaltyEGP != null && receipt.penaltyEGP > 0 ? (
                    <div className="flex justify-between text-emerald-700 font-medium">
                      <span>Late Penalty (Paid):</span>
                      <span>+ EGP {receipt.penaltyEGP.toLocaleString()}</span>
                    </div>
                  ) : selectedPenaltySum > 0 ? (
                    <div className="flex justify-between text-amber-700 font-medium bg-amber-50 px-2 py-1 rounded">
                      <span>Late Penalty (Unpaid):</span>
                      <span>+ EGP {selectedPenaltySum.toLocaleString()} (Uncollected)</span>
                    </div>
                  ) : null}

                  <div className="flex justify-between text-gray-600">
                    <span>Remaining Balance:</span>
                    <span>EGP {receipt.remainingBalanceEGP.toLocaleString()}</span>
                  </div>
                </div>

                <div className="text-center text-[10px] text-gray-400 pt-1">
                  *** MERCHANT COPY - APPROVED BY ISSUER ***
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Footer controls */}
        <div className="px-6 py-3.5 bg-gray-50 border-t border-gray-100 flex items-center justify-between">
          {step === 1 && (
            <>
              <Button variant="secondary" size="sm" onClick={onClose}>
                Cancel
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={() => setStep(2)}
                disabled={selectedFeeIds.length === 0}
                className="bg-orange-600 hover:bg-orange-700 text-white font-medium"
              >
                Next: Configure Amount ({selectedFeeIds.length} fees) &rarr;
              </Button>
            </>
          )}

          {step === 2 && (
            <>
              <Button variant="secondary" size="sm" onClick={() => setStep(1)}>
                &larr; Back
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={() => setStep(3)}
                disabled={effectiveAmount <= 0}
                className="bg-orange-600 hover:bg-orange-700 text-white font-medium"
              >
                Next: Authorize on POS Terminal &rarr;
              </Button>
            </>
          )}

          {step === 3 && (
            <>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => {
                  setStep(2);
                  setPosState("idle");
                  setPosAuthRef("");
                }}
                disabled={processing}
              >
                &larr; Back
              </Button>
              {posState !== "approved" && (
                <span className="text-xs text-gray-400">
                  Please tap/insert card on POS terminal to continue
                </span>
              )}
            </>
          )}

          {step === 4 && (
            <div className="flex items-center justify-between w-full">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => window.print()}
                className="flex items-center gap-1.5"
              >
                <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4a2 2 0 00-2-2H9a2 2 0 00-2 2v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H9a2 2 0 00-2 2v4h10z" />
                </svg>
                Print Receipt
              </Button>
              <Button
                variant="primary"
                size="sm"
                onClick={() => {
                  onClose();
                  onSuccess();
                }}
                className="bg-emerald-600 hover:bg-emerald-700 text-white font-semibold"
              >
                Done &amp; Refresh Payments
              </Button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
