"use client";

import { useCallback, useEffect, useState } from "react";
import { useApiClient, type PageResponse } from "@tuition/api-client";
import {
  Button,
  LoadingSpinner,
  Pagination,
  ReconcileIcon,
  CheckCircleIcon,
  ClockIcon,
  AlertIcon,
  ExclamationIcon,
  EyeIcon,
  UserIcon,
  ChevronLeftIcon,
  CheckIcon,
  RefreshIcon,
  DownloadIcon,
  formatIsoDate,
} from "@tuition/ui";
import type {
  ReconciliationSummaryDto,
  ReconciliationRunDto,
  ReconciliationExceptionDto,
  ReconciliationExceptionDetailDto,
  ResolutionRequest,
} from "./types";
import {
  excStatusClass,
  priorityClass,
  runStatusClass,
  isExceptionRun,
  institutionTypePill,
  money,
  runRef,
  excRef,
  initials,
} from "./badges";

type Tab = "summary" | "exceptions";

const REASON_OPTIONS = [
  "Duplicate entry by institution",
  "System timing difference",
  "Manual adjustment not synced",
  "Fee reversal recorded late",
  "Data entry error",
  "Bank processing delay",
];

const ACTION_OPTIONS = [
  "Adjust institution record to match bank",
  "Reverse duplicate transaction",
  "Manual override — accept bank figure",
  "Request reconfirmation from institution",
  "Escalate to Operations Manager",
];

const RESOLUTION_STATUSES = ["Open", "Under Investigation", "Resolved", "Escalated"];

function ExceptionResolutionView({
  excId,
  assignees,
  onBack,
  onSaved,
}: {
  excId: string;
  assignees: string[];
  onBack: () => void;
  onSaved: () => void;
}) {
  const apiClient = useApiClient();
  const [detail, setDetail] = useState<ReconciliationExceptionDetailDto | null>(null);
  const [loading, setLoading] = useState(true);

  const [resStatus, setResStatus] = useState("Open");
  const [assignedTo, setAssignedTo] = useState("");
  const [reason, setReason] = useState("");
  const [resolutionAction, setResolutionAction] = useState("");
  const [supportingReference, setSupportingReference] = useState("");
  const [notes, setNotes] = useState("");

  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    let ignore = false;
    apiClient
      .get<ReconciliationExceptionDetailDto>(`/reconciliation/exceptions/${excId}`)
      .then((dto) => {
        if (ignore) return;
        setDetail(dto);
        setResStatus(dto.status);
        setAssignedTo(dto.assignedTo ?? "");
        setReason(dto.reason ?? "");
        setResolutionAction(dto.resolutionAction ?? "");
        setSupportingReference(dto.supportingReference ?? "");
        setNotes(dto.notes ?? "");
      })
      .finally(() => !ignore && setLoading(false));
    return () => {
      ignore = true;
    };
  }, [apiClient, excId]);

  const handleSave = async () => {
    if (resStatus === "Resolved" && !resolutionAction.trim()) {
      setSaveError("Resolution Action is required when resolving an exception.");
      return;
    }
    setSaving(true);
    setSaveError(null);
    try {
      const payload: ResolutionRequest = {
        status: resStatus,
        assignedTo: assignedTo || undefined,
        reason: reason || undefined,
        resolutionAction: resolutionAction || undefined,
        supportingReference: supportingReference || undefined,
        notes: notes || undefined,
      };
      await apiClient.patch<ReconciliationExceptionDto>(`/reconciliation/exceptions/${excId}`, payload);
      const refreshed = await apiClient.get<ReconciliationExceptionDetailDto>(
        `/reconciliation/exceptions/${excId}`
      );
      setDetail(refreshed);
      setSaved(true);
      onSaved();
      setTimeout(() => setSaved(false), 3500);
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : "Failed to save resolution.");
    } finally {
      setSaving(false);
    }
  };

  if (loading || !detail) {
    return (
      <div className="flex flex-col items-center justify-center py-24 space-y-3">
        <LoadingSpinner />
        <p className="text-sm text-gray-500">Loading exception details...</p>
      </div>
    );
  }

  const rows = [
    {
      field: "Reference",
      payment: detail.txRef || "—",
      bank: detail.bankRef || "—",
      school: detail.feeRef || "—",
    },
    {
      field: "Amount (EGP)",
      payment: money(detail.systemAmountEGP),
      bank: money(detail.bankAmountEGP),
      school: money(detail.schoolAmountEGP),
    },
    {
      field: "Status",
      payment: detail.txStatus || "—",
      bank: detail.bankStatus || "—",
      school: "Recorded",
    },
    {
      field: "Date",
      payment: detail.date ? formatIsoDate(detail.date) : "—",
      bank: detail.settlementDate ? formatIsoDate(detail.settlementDate) : "—",
      school: detail.collectionDate ? formatIsoDate(detail.collectionDate) : "—",
    },
    {
      field: "Method / Channel",
      payment: detail.payMethod || "—",
      bank: "Batch settlement",
      school: "Term fees",
    },
  ];

  const bankAmount = detail.bankAmountEGP ?? 0;
  const amountMismatch = (raw: string) => {
    const n = parseInt(raw.replace(/,/g, ""));
    return !isNaN(n) && n !== bankAmount;
  };

  return (
    <div className="space-y-4">
      <button
        onClick={onBack}
        className="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-700 transition-colors"
      >
        <ChevronLeftIcon className="w-4 h-4" /> Back to Reconciliation
      </button>

      {saved && (
        <div className="flex items-center gap-2 bg-green-50 border border-green-200 rounded-xl px-5 py-3 text-green-800 text-sm font-semibold">
          <CheckCircleIcon className="w-4 h-4" /> Exception resolution saved. Status updated to{" "}
          <span className="ml-1">{resStatus}</span>.
        </div>
      )}

      <div className="grid grid-cols-3 gap-4">
        <div className="col-span-2 space-y-4">
          <div className="bg-white rounded-xl border border-[#E8EDF5] p-6">
            <div className="flex items-start justify-between mb-4 pb-4 border-b border-gray-100">
              <div>
                <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">
                  Reconciliation Exception
                </div>
                <h2 className="text-lg font-bold text-[#1B2A4A] font-mono">{excRef(detail.id)}</h2>
                <p className="text-xs text-gray-400 mt-0.5">
                  {detail.date ? formatIsoDate(detail.date) : "—"} · {detail.institution || "—"}
                  <span className={`ml-1.5 text-[10px] font-semibold px-1.5 py-0.5 rounded ${institutionTypePill(detail.institutionType)}`}>
                    {detail.institutionType || "—"}
                  </span>
                </p>
              </div>
              <div className="flex items-center gap-2">
                <span className={`text-[11px] font-semibold px-2 py-0.5 rounded ${priorityClass(detail.priority)}`}>
                  {detail.priority || "Medium"} Priority
                </span>
                <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${excStatusClass(resStatus)}`}>
                  {resStatus}
                </span>
              </div>
            </div>

            <div className="flex items-start gap-3 bg-red-50 border border-red-100 rounded-lg px-4 py-3 mb-5">
              <ExclamationIcon className="w-4 h-4 text-red-500 shrink-0 mt-0.5" />
              <div>
                <div className="text-xs font-semibold text-red-700">{detail.type || "Discrepancy detected"}</div>
                <div className="text-xs text-red-500 mt-0.5">
                  Discrepancy of <strong className="font-mono">EGP {money(detail.differenceEGP)}</strong> detected
                  between records.
                </div>
              </div>
            </div>

            <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-3">Record Comparison</h3>
            <div className="rounded-xl border border-[#E8EDF5] overflow-hidden">
              <table className="w-full text-xs">
                <thead>
                  <tr className="bg-[#F8FAFD]">
                    <th className="text-left px-4 py-2.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider w-28">
                      Field
                    </th>
                    <th className="text-left px-4 py-2.5 text-[11px] font-semibold text-[#003087] uppercase tracking-wider border-l border-[#E8EDF5]">
                      Payment Record
                    </th>
                    <th className="text-left px-4 py-2.5 text-[11px] font-semibold text-indigo-600 uppercase tracking-wider border-l border-[#E8EDF5]">
                      Bank Transaction
                    </th>
                    <th className="text-left px-4 py-2.5 text-[11px] font-semibold text-amber-600 uppercase tracking-wider border-l border-[#E8EDF5]">
                      School Fee
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {rows.map((row) => {
                    const payMismatch = row.field === "Amount (EGP)" && amountMismatch(row.payment);
                    const sclMismatch = row.field === "Amount (EGP)" && amountMismatch(row.school);
                    return (
                      <tr key={row.field} className={row.field === "Amount (EGP)" ? "bg-red-50/40" : ""}>
                        <td className="px-4 py-3 font-semibold text-gray-500 whitespace-nowrap">{row.field}</td>
                        <td
                          className={`px-4 py-3 border-l border-[#E8EDF5] font-mono ${
                            payMismatch ? "text-red-600 font-bold" : "text-gray-700"
                          }`}
                        >
                          {row.payment}
                          {payMismatch && (
                            <span className="ml-2 text-[10px] font-semibold bg-red-100 text-red-600 px-1.5 py-0.5 rounded">
                              Δ {money(Math.abs((detail.systemAmountEGP ?? 0) - bankAmount))}
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 border-l border-[#E8EDF5] font-mono text-gray-700">{row.bank}</td>
                        <td
                          className={`px-4 py-3 border-l border-[#E8EDF5] font-mono ${
                            sclMismatch ? "text-red-600 font-bold" : "text-gray-700"
                          }`}
                        >
                          {row.school}
                          {sclMismatch && (
                            <span className="ml-2 text-[10px] font-semibold bg-red-100 text-red-600 px-1.5 py-0.5 rounded">
                              Δ {money(Math.abs((detail.schoolAmountEGP ?? 0) - bankAmount))}
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>

          <div className="bg-white rounded-xl border border-[#E8EDF5] p-6">
            <h3 className="text-sm font-semibold text-[#1B2A4A] mb-4">Resolution Details</h3>
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                    Assign To
                  </label>
                  <div className="relative">
                    <UserIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
                    <select
                      value={assignedTo}
                      onChange={(e) => setAssignedTo(e.target.value)}
                      className="w-full pl-8 border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                    >
                      <option value="">Unassigned</option>
                      {assignees.map((a) => (
                        <option key={a}>{a}</option>
                      ))}
                    </select>
                  </div>
                  {assignedTo && (
                    <p className="text-[11px] text-gray-400 mt-1">
                      Currently assigned to <strong className="text-gray-600">{assignedTo}</strong>
                    </p>
                  )}
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                    Resolution Status
                  </label>
                  <select
                    value={resStatus}
                    onChange={(e) => setResStatus(e.target.value)}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                  >
                    {RESOLUTION_STATUSES.map((s) => (
                      <option key={s}>{s}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                    Exception Reason
                  </label>
                  <select
                    value={reason}
                    onChange={(e) => setReason(e.target.value)}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                  >
                    <option value="">Select reason…</option>
                    {REASON_OPTIONS.map((r) => (
                      <option key={r}>{r}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                    Resolution Action {resStatus === "Resolved" && <span className="text-red-500">*</span>}
                  </label>
                  <select
                    value={resolutionAction}
                    onChange={(e) => setResolutionAction(e.target.value)}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                  >
                    <option value="">Select action…</option>
                    {ACTION_OPTIONS.map((a) => (
                      <option key={a}>{a}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                  Supporting Reference
                </label>
                <input
                  type="text"
                  value={supportingReference}
                  onChange={(e) => setSupportingReference(e.target.value)}
                  placeholder="Bank advice number, email thread ID, ticket…"
                  className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                />
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                  Investigation Notes
                </label>
                <textarea
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Add investigation notes or context…"
                  rows={3}
                  className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2.5 text-sm text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] resize-none"
                />
              </div>

              {saveError && (
                <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-red-700 text-xs flex items-center gap-2">
                  <AlertIcon className="w-4 h-4 shrink-0 text-red-500" />
                  <span>{saveError}</span>
                </div>
              )}

              <div className="flex items-center justify-end gap-3 pt-1">
                <button
                  onClick={onBack}
                  className="px-4 py-2 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handleSave}
                  disabled={saving}
                  className="px-5 py-2 text-sm font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center gap-1.5 disabled:opacity-50"
                >
                  <CheckIcon className="w-3.5 h-3.5" /> {saving ? "Saving…" : "Save Resolution"}
                </button>
              </div>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-xl border border-[#E8EDF5] p-5 self-start">
          <h3 className="text-sm font-semibold text-[#1B2A4A] mb-4">Investigation Workflow</h3>
          <div className="space-y-3">
            {detail.workflow.map((step, i) => (
              <div key={step.step} className="flex items-start gap-3">
                <div
                  className={`w-5 h-5 rounded-full flex items-center justify-center shrink-0 mt-0.5 text-[10px] font-bold ${
                    step.done ? "bg-[#003087] text-white" : "bg-gray-100 text-gray-400"
                  }`}
                >
                  {step.done ? <CheckIcon className="w-2.5 h-2.5" /> : i + 1}
                </div>
                <div>
                  <div className={`text-xs font-semibold ${step.done ? "text-gray-800" : "text-gray-400"}`}>
                    {step.step}
                  </div>
                  <div className="text-[10px] text-gray-400">{step.desc}</div>
                </div>
              </div>
            ))}
          </div>

          <div className="mt-5 pt-4 border-t border-gray-100">
            <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2">SLA Status</div>
            <div className="flex items-center gap-2">
              <div className="flex-1 h-1.5 bg-gray-100 rounded-full overflow-hidden">
                <div
                  className="h-full bg-amber-400 rounded-full"
                  style={{ width: `${detail.sla.percent}%` }}
                />
              </div>
              <span className="text-[11px] font-semibold text-amber-600">{detail.sla.timeLeft} left</span>
            </div>
          </div>

          {assignedTo && (
            <div className="mt-4 pt-4 border-t border-gray-100">
              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-2">
                Assigned To
              </div>
              <div className="flex items-center gap-2">
                <div className="w-6 h-6 rounded-full bg-[#003087] text-white flex items-center justify-center text-[10px] font-bold shrink-0">
                  {initials(assignedTo)}
                </div>
                <span className="text-xs font-semibold text-gray-700">{assignedTo}</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default function ReconciliationPage() {
  const apiClient = useApiClient();

  const [summary, setSummary] = useState<ReconciliationSummaryDto | null>(null);
  const [tab, setTab] = useState<Tab>("summary");
  const [runs, setRuns] = useState<ReconciliationRunDto[]>([]);
  const [runsPage, setRunsPage] = useState(1);
  const [runsTotalPages, setRunsTotalPages] = useState(1);
  const [exceptions, setExceptions] = useState<ReconciliationExceptionDto[]>([]);
  const [assignees, setAssignees] = useState<string[]>([]);
  const [showResolved, setShowResolved] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [isExporting, setIsExporting] = useState(false);
  const [selectedExcId, setSelectedExcId] = useState<string | null>(null);

  const loadAll = useCallback(() => {
    Promise.all([
      apiClient.get<ReconciliationSummaryDto>("/reconciliation/summary"),
      apiClient.get<PageResponse<ReconciliationRunDto>>(`/reconciliation/runs?page=${runsPage}&pageSize=20`),
      apiClient.get<PageResponse<ReconciliationExceptionDto>>(
        "/reconciliation/exceptions?includeResolved=true&page=1&pageSize=100"
      ),
      apiClient.get<string[]>("/reconciliation/assignees"),
    ])
      .then(([summaryRes, runsRes, excRes, assigneesRes]) => {
        setSummary(summaryRes);
        setRuns(runsRes.data ?? []);
        setRunsTotalPages(Math.max(1, runsRes.totalPages ?? 1));
        setExceptions(excRes.data ?? []);
        setAssignees(assigneesRes ?? []);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [apiClient, runsPage]);

  useEffect(() => {
    loadAll();
  }, [loadAll, refreshTrigger]);

  const handleExportCsv = async () => {
    setIsExporting(true);
    try {
      let accessToken = "";
      try {
        const raw = localStorage.getItem("tuition.auth.session");
        accessToken = raw ? JSON.parse(raw).accessToken ?? "" : "";
      } catch {
        // fall through with an empty token; the request will 401 and surface the error below
      }
      const res = await fetch("http://localhost:8080/api/v1/reconciliation/export?format=csv", {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (!res.ok) throw new Error("Failed to export reconciliation report");
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `reconciliation-export-${new Date().toISOString().slice(0, 10)}.csv`;
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

  const handleInvestigate = (run: ReconciliationRunDto) => {
    const match = exceptions.find((e) => e.reconRowId === run.id);
    setTab("exceptions");
    if (match) setSelectedExcId(match.id);
  };

  if (selectedExcId) {
    return (
      <ExceptionResolutionView
        key={selectedExcId}
        excId={selectedExcId}
        assignees={assignees}
        onBack={() => setSelectedExcId(null)}
        onSaved={() => setRefreshTrigger((p) => p + 1)}
      />
    );
  }

  const visibleExceptions = exceptions.filter((e) => showResolved || e.status !== "Resolved");
  const resolvedCount = exceptions.filter((e) => e.status === "Resolved").length;
  const unresolvedCount = exceptions.filter((e) => e.status !== "Resolved").length;

  const kpis = [
    {
      title: "Total Transactions",
      value: (summary?.totalTransactions ?? 0).toLocaleString(),
      Icon: ReconcileIcon,
      color: "bg-[#003087]/10 text-[#003087]",
    },
    {
      title: "Matched",
      value: (summary?.matched ?? 0).toLocaleString(),
      Icon: CheckCircleIcon,
      color: "bg-green-50 text-green-600",
    },
    {
      title: "Pending",
      value: (summary?.pending ?? 0).toLocaleString(),
      Icon: ClockIcon,
      color: "bg-amber-50 text-amber-600",
    },
    {
      title: "Exceptions",
      value: (summary?.pendingExceptions ?? 0).toLocaleString(),
      Icon: AlertIcon,
      color: "bg-red-50 text-red-500",
    },
  ];

  return (
    <div className="space-y-4 pb-12">
      <div>
        <h1 className="text-xl font-bold tracking-tight" style={{ color: "var(--cib-blue)" }}>
          Reconciliation
        </h1>
        <p className="text-xs text-gray-500 mt-1">
          3-way reconciliation between bank settlement, the tuition network ledger, and institution records.
        </p>
      </div>

      {loading && runs.length === 0 && exceptions.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-24 space-y-3">
          <LoadingSpinner />
          <p className="text-sm text-gray-500">Loading reconciliation data...</p>
        </div>
      ) : (
        <>
          <div className="grid grid-cols-4 gap-4">
            {kpis.map(({ title, value, Icon, color }) => (
              <div key={title} className="bg-white rounded-xl border border-[#E8EDF5] p-5">
                <div className="flex items-start justify-between">
                  <div>
                    <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">{title}</p>
                    <p className="text-2xl font-bold text-[#1B2A4A] mt-1">{value}</p>
                  </div>
                  <div className={`w-10 h-10 rounded-xl flex items-center justify-center ${color}`}>
                    <Icon className="w-5 h-5" />
                  </div>
                </div>
              </div>
            ))}
          </div>

          <div className="flex items-center gap-1 bg-white rounded-xl border border-[#E8EDF5] p-1.5">
            <button
              onClick={() => setTab("summary")}
              className={`px-4 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                tab === "summary" ? "bg-[#003087] text-white" : "text-gray-500 hover:bg-gray-50"
              }`}
            >
              Reconciliation Summary
            </button>
            <button
              onClick={() => setTab("exceptions")}
              className={`px-4 py-1.5 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 ${
                tab === "exceptions" ? "bg-[#003087] text-white" : "text-gray-500 hover:bg-gray-50"
              }`}
            >
              Exception Queue
              <span
                className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full ${
                  tab === "exceptions" ? "bg-white/20 text-white" : "bg-red-100 text-red-600"
                }`}
              >
                {unresolvedCount}
              </span>
            </button>
            <div className="flex-1" />
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setRefreshTrigger((p) => p + 1)}
              className="flex items-center gap-1.5"
            >
              <RefreshIcon className="w-3.5 h-3.5" /> Refresh
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={handleExportCsv}
              disabled={isExporting}
              className="flex items-center gap-1.5"
            >
              <DownloadIcon className="w-3.5 h-3.5" /> {isExporting ? "Exporting..." : "Export"}
            </Button>
          </div>

          {tab === "summary" && (
            <div className="space-y-3">
              <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-[#F8FAFD] border-b border-[#E8EDF5]">
                        {[
                          "Reference",
                          "Institution",
                          "Type",
                          "Date",
                          "Tx Count",
                          "Bank Amount (EGP)",
                          "System Amount (EGP)",
                          "Inst. Amount (EGP)",
                          "Status",
                          "",
                        ].map((h) => (
                          <th
                            key={h}
                            className="text-left px-5 py-3.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap"
                          >
                            {h}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-50">
                      {runs.length === 0 && (
                        <tr>
                          <td colSpan={10} className="px-5 py-8 text-center text-sm text-gray-400">
                            No reconciliation runs yet.
                          </td>
                        </tr>
                      )}
                      {runs.map((run) => {
                        const bank = run.bankAmountEGP ?? 0;
                        const system = run.systemAmountEGP ?? 0;
                        const school = run.schoolAmountEGP ?? 0;
                        const exception = isExceptionRun(run.status);
                        return (
                          <tr
                            key={run.id}
                            className={`transition-colors ${
                              exception ? "bg-red-50/20 hover:bg-red-50/40" : "hover:bg-[#F8FAFD]"
                            }`}
                          >
                            <td className="px-5 py-4 font-mono text-xs text-[#003087] font-semibold whitespace-nowrap">
                              {runRef(run.id)}
                            </td>
                            <td className="px-5 py-4 text-xs text-gray-700">{run.institution || "—"}</td>
                            <td className="px-5 py-4 whitespace-nowrap">
                              <span
                                className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${institutionTypePill(
                                  run.institutionType
                                )}`}
                              >
                                {run.institutionType || "—"}
                              </span>
                            </td>
                            <td className="px-5 py-4 text-xs text-gray-400 whitespace-nowrap">
                              {run.date ? formatIsoDate(run.date) : "—"}
                            </td>
                            <td className="px-5 py-4 text-xs font-mono font-semibold text-gray-700">
                              {run.txCount ?? 0}
                            </td>
                            <td className="px-5 py-4 text-xs font-mono font-semibold text-gray-800">
                              {money(bank)}
                            </td>
                            <td
                              className={`px-5 py-4 text-xs font-mono font-semibold ${
                                system !== bank ? "text-red-600" : "text-gray-800"
                              }`}
                            >
                              {money(system)}
                              {system !== bank && <span className="ml-1 text-[10px]">↑</span>}
                            </td>
                            <td
                              className={`px-5 py-4 text-xs font-mono font-semibold ${
                                school !== bank ? "text-red-600" : "text-gray-800"
                              }`}
                            >
                              {money(school)}
                              {school !== bank && <span className="ml-1 text-[10px]">↓</span>}
                            </td>
                            <td className="px-5 py-4">
                              <span
                                className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${runStatusClass(
                                  run.status
                                )}`}
                              >
                                {run.status}
                              </span>
                            </td>
                            <td className="px-5 py-4">
                              {exception && (
                                <button
                                  onClick={() => handleInvestigate(run)}
                                  className="text-[11px] text-red-600 font-semibold border border-red-200 rounded px-2 py-0.5 hover:bg-red-50 transition-colors"
                                >
                                  Investigate
                                </button>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>
              <Pagination page={runsPage} totalPages={runsTotalPages} onPageChange={setRunsPage} />
            </div>
          )}

          {tab === "exceptions" && (
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <p className="text-xs text-gray-400">
                  {visibleExceptions.length} {showResolved ? "total" : "unresolved"} exception
                  {visibleExceptions.length !== 1 ? "s" : ""}
                  {!showResolved && resolvedCount > 0 && <span className="ml-1">· {resolvedCount} resolved hidden</span>}
                </p>
                {resolvedCount > 0 && (
                  <button
                    onClick={() => setShowResolved((v) => !v)}
                    className="text-xs font-semibold text-[#003087] hover:underline"
                  >
                    {showResolved ? "Hide resolved" : `Show ${resolvedCount} resolved`}
                  </button>
                )}
              </div>

              {visibleExceptions.length === 0 && (
                <div className="bg-white rounded-xl border border-[#E8EDF5] p-10 text-center">
                  <CheckCircleIcon className="w-10 h-10 text-green-400 mx-auto mb-3" />
                  <p className="text-sm font-semibold text-gray-600">No unresolved exceptions</p>
                  <p className="text-xs text-gray-400 mt-1">All reconciliation exceptions have been resolved.</p>
                </div>
              )}

              {visibleExceptions.map((exc) => {
                const isResolved = exc.status === "Resolved";
                const bank = exc.bankAmountEGP ?? 0;
                const system = exc.systemAmountEGP ?? 0;
                const school = exc.schoolAmountEGP ?? 0;
                return (
                  <div
                    key={exc.id}
                    className={`bg-white rounded-xl border p-5 ${
                      isResolved ? "border-gray-100 opacity-70" : "border-red-100"
                    }`}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-start gap-3 min-w-0">
                        <div
                          className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${
                            isResolved ? "bg-green-50" : "bg-red-50"
                          }`}
                        >
                          {isResolved ? (
                            <CheckCircleIcon className="w-5 h-5 text-green-500" />
                          ) : (
                            <ExclamationIcon className="w-5 h-5 text-red-500" />
                          )}
                        </div>
                        <div className="min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="font-mono text-xs font-bold text-[#003087]">{excRef(exc.id)}</span>
                            <span className="text-gray-200">·</span>
                            <span className="font-mono text-[11px] text-gray-400">{exc.txRef || "—"}</span>
                            <span className="text-gray-200">·</span>
                            <span className="text-xs text-gray-400">
                              {exc.date ? formatIsoDate(exc.date) : "—"}
                            </span>
                            <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${priorityClass(exc.priority)}`}>
                              {exc.priority || "Medium"}
                            </span>
                          </div>
                          <div className="flex items-center gap-2 mt-0.5 flex-wrap">
                            <span className="text-sm font-semibold text-gray-800">{exc.institution || "—"}</span>
                            <span
                              className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${institutionTypePill(
                                exc.institutionType
                              )}`}
                            >
                              {exc.institutionType || "—"}
                            </span>
                          </div>
                          <div className="text-xs text-red-600 mt-0.5">{exc.type || "Discrepancy"}</div>
                          {exc.assignedTo && (
                            <div className="flex items-center gap-1 mt-1.5 text-[11px] text-gray-400">
                              <UserIcon className="w-3 h-3" />
                              <span>
                                Assigned to <strong className="text-gray-600">{exc.assignedTo}</strong>
                              </span>
                            </div>
                          )}
                        </div>
                      </div>

                      <div className="flex items-center gap-3 shrink-0">
                        <span className={`text-[11px] font-semibold px-2 py-0.5 rounded border ${excStatusClass(exc.status)}`}>
                          {exc.status}
                        </span>
                        <div className="text-right">
                          <div className="text-[10px] text-gray-400">Difference</div>
                          <div className={`text-base font-bold font-mono ${isResolved ? "text-gray-400" : "text-red-600"}`}>
                            {money(exc.differenceEGP)} EGP
                          </div>
                        </div>
                        <button
                          onClick={() => setSelectedExcId(exc.id)}
                          className={
                            isResolved
                              ? "px-3 py-2 text-xs font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors text-gray-500 flex items-center gap-1.5"
                              : "px-3 py-2 text-xs font-semibold text-white bg-[#003087] rounded-lg hover:bg-[#002060] transition-colors flex items-center gap-1.5"
                          }
                        >
                          <EyeIcon className="w-3.5 h-3.5" /> {isResolved ? "View" : "Resolve"}
                        </button>
                      </div>
                    </div>

                    <div className="mt-3 pt-3 border-t border-gray-100 grid grid-cols-3 gap-4">
                      {[
                        { label: "Bank", value: bank },
                        { label: "System", value: system },
                        { label: "School", value: school },
                      ].map(({ label, value }) => (
                        <div key={label} className="text-xs">
                          <span className="text-gray-400">{label}: </span>
                          <span className={`font-mono font-semibold ${value !== bank ? "text-red-600" : "text-gray-700"}`}>
                            {money(value)} EGP
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </>
      )}
    </div>
  );
}
