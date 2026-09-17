"use client";

import { useCallback, useEffect, useState } from "react";
import { useApiClient } from "@tuition/api-client";
import {
  Table,
  type Column,
  Badge,
  type BadgeTone,
  Button,
  Modal,
  Pagination,
  LoadingSpinner,
  useToast,
  SearchIcon,
  RefreshIcon,
  DownloadIcon,
  CheckCircleIcon,
  ClockIcon,
  AlertIcon,
} from "@tuition/ui";
import type {
  ReconciliationSummaryDto,
  ReconciliationRunDto,
  ReconciliationRunDetailDto,
  ReconciliationExceptionDto,
  ReconciliationExceptionDetailDto,
} from "./types";
import { INPUT_CLASSES } from "./formStyles";

const RUN_STATUS_TONE: Record<string, BadgeTone> = {
  Matched: "success",
  Exception: "danger",
  "Exceptions Found": "danger",
  Pending: "warning",
};

const EXCEPTION_STATUS_TONE: Record<string, BadgeTone> = {
  Open: "danger",
  "Under Investigation": "warning",
  Escalated: "danger",
  Resolved: "success",
};

const PRIORITY_TONE: Record<string, BadgeTone> = {
  High: "danger",
  Medium: "warning",
  Low: "info",
};

type Tab = "runs" | "exceptions";

export default function ReconciliationPage() {
  const apiClient = useApiClient();

  const [summary, setSummary] = useState<ReconciliationSummaryDto | null>(null);
  const [tab, setTab] = useState<Tab>("runs");

  const loadSummary = useCallback(() => {
    apiClient.get<ReconciliationSummaryDto>("/reconciliation/summary").then(setSummary).catch(() => {});
  }, [apiClient]);

  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <SummaryCard
          icon={<CheckCircleIcon className="w-4 h-4" />}
          label="Matched"
          value={summary ? summary.matched.toLocaleString() : "-"}
          tone="success"
        />
        <SummaryCard
          icon={<ClockIcon className="w-4 h-4" />}
          label="Pending"
          value={summary ? summary.pending.toLocaleString() : "-"}
          tone="warning"
        />
        <SummaryCard
          icon={<AlertIcon className="w-4 h-4" />}
          label="Open Exceptions"
          value={summary ? summary.pendingExceptions.toLocaleString() : "-"}
          tone="danger"
        />
        <SummaryCard
          icon={<CheckCircleIcon className="w-4 h-4" />}
          label="Total Transactions"
          value={summary ? summary.totalTransactions.toLocaleString() : "-"}
          tone="info"
        />
      </div>

      <div className="flex items-center gap-1 border-b border-[var(--cib-border)]">
        <TabButton active={tab === "runs"} onClick={() => setTab("runs")}>
          Reconciliation Runs
        </TabButton>
        <TabButton active={tab === "exceptions"} onClick={() => setTab("exceptions")}>
          Exceptions
          {summary && summary.pendingExceptions > 0 && (
            <span className="ml-1.5 inline-flex items-center justify-center rounded-full bg-red-100 text-red-700 text-[10px] font-semibold px-1.5 py-0.5">
              {summary.pendingExceptions}
            </span>
          )}
        </TabButton>
      </div>

      {tab === "runs" ? <RunsTab onRunTriggered={loadSummary} /> : <ExceptionsTab onExceptionResolved={loadSummary} />}
    </div>
  );
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className="px-4 py-2.5 text-sm font-semibold relative transition-colors flex items-center"
      style={{ color: active ? "var(--cib-blue)" : "var(--cib-text-muted)" }}
    >
      {children}
      {active && <span className="absolute left-0 right-0 bottom-0 h-0.5" style={{ background: "var(--cib-blue)" }} />}
    </button>
  );
}

function SummaryCard({
  icon,
  label,
  value,
  tone,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  tone: BadgeTone;
}) {
  const toneBg: Record<BadgeTone, string> = {
    success: "bg-green-50 text-green-700",
    warning: "bg-[var(--cib-orange-light)] text-[var(--cib-orange-dark)]",
    danger: "bg-red-50 text-red-700",
    info: "bg-[var(--cib-blue-light)] text-[var(--cib-blue)]",
    neutral: "bg-gray-100 text-gray-700",
  };
  return (
    <div className="bg-white rounded-xl border p-4 flex items-center gap-3" style={{ borderColor: "#E8EDF5" }}>
      <div className={`w-9 h-9 rounded-lg flex items-center justify-center ${toneBg[tone]}`}>{icon}</div>
      <div>
        <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">{label}</p>
        <p className="text-lg font-bold" style={{ color: "var(--cib-text)" }}>
          {value}
        </p>
      </div>
    </div>
  );
}

function money(n: number | undefined): string {
  return n === undefined ? "-" : `EGP ${Math.round(n).toLocaleString()}`;
}

// ---------------------------------------------------------------------------
// Runs tab
// ---------------------------------------------------------------------------

interface RunsPage {
  data: ReconciliationRunDto[];
  page: number;
  totalPages: number;
  total: number;
}

function RunsTab({ onRunTriggered }: { onRunTriggered: () => void }) {
  const apiClient = useApiClient();
  const toast = useToast();

  const [result, setResult] = useState<RunsPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loading = result === null && error === null;

  const [status, setStatus] = useState("");
  const [institution, setInstitution] = useState("");
  const [page, setPage] = useState(1);
  const [triggering, setTriggering] = useState(false);

  const [selectedRunId, setSelectedRunId] = useState<string | null>(null);
  const [runDetail, setRunDetail] = useState<ReconciliationRunDetailDto | null>(null);

  const load = useCallback(() => {
    const params = new URLSearchParams();
    if (status) params.set("status", status);
    if (institution.trim()) params.set("institution", institution.trim());
    params.set("page", String(page - 1));
    params.set("pageSize", "20");

    apiClient
      .get<RunsPage>(`/reconciliation/runs?${params.toString()}`)
      .then((data) => {
        setResult(data);
        setError(null);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load reconciliation runs"));
  }, [apiClient, status, institution, page]);

  useEffect(() => {
    load();
  }, [load]);

  function openRun(id: string) {
    setSelectedRunId(id);
    setRunDetail(null);
    apiClient
      .get<ReconciliationRunDetailDto>(`/reconciliation/runs/${id}`)
      .then(setRunDetail)
      .catch(() => setRunDetail(null));
  }

  async function triggerRun() {
    setTriggering(true);
    try {
      await apiClient.post("/reconciliation/runs", {});
      toast.show("Reconciliation run triggered", "success");
      load();
      onRunTriggered();
    } catch (err) {
      toast.show(err instanceof Error ? err.message : "Failed to trigger run", "error");
    } finally {
      setTriggering(false);
    }
  }

  const columns: Column<ReconciliationRunDto>[] = [
    {
      key: "institution",
      header: "Institution",
      render: (r) => (
        <div>
          <div className="font-semibold">{r.institution}</div>
          <div className="text-[11px] text-[var(--cib-text-muted)]">{r.institutionType}</div>
        </div>
      ),
    },
    { key: "date", header: "Run Date" },
    { key: "totalTransactions", header: "Transactions", render: (r) => (r.totalTransactions ?? r.txCount ?? 0).toLocaleString() },
    { key: "matchedCount", header: "Matched", render: (r) => (r.matchedCount ?? 0).toLocaleString() },
    {
      key: "exceptionCount",
      header: "Exceptions",
      render: (r) => (
        <span className={r.exceptionCount && r.exceptionCount > 0 ? "text-red-600 font-semibold" : ""}>
          {(r.exceptionCount ?? 0).toLocaleString()}
        </span>
      ),
    },
    { key: "bankAmountEGP", header: "Bank Amount", render: (r) => money(r.bankAmountEGP) },
    { key: "systemAmountEGP", header: "System Amount", render: (r) => money(r.systemAmountEGP) },
    { key: "status", header: "Status", render: (r) => <Badge tone={RUN_STATUS_TONE[r.status] ?? "neutral"}>{r.status}</Badge> },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 flex-1 max-w-md">
          <div className="relative flex-1">
            <SearchIcon className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[var(--cib-text-muted)]" />
            <input
              value={institution}
              onChange={(e) => {
                setInstitution(e.target.value);
                setPage(1);
              }}
              placeholder="Filter by institution"
              className="w-full pl-9 pr-3 py-2 text-sm rounded-md border border-[var(--cib-border)] bg-white"
            />
          </div>
        </div>
        <div className="flex items-center gap-2">
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(1);
            }}
            className="text-sm rounded-md border border-[var(--cib-border)] bg-white px-2.5 py-2"
          >
            <option value="">All statuses</option>
            <option value="Matched">Matched</option>
            <option value="Exception">Exception</option>
            <option value="Pending">Pending</option>
          </select>
          <Button onClick={triggerRun} loading={triggering} size="sm">
            <RefreshIcon className="w-4 h-4" />
            Trigger Run
          </Button>
        </div>
      </div>

      {error ? (
        <div className="bg-white rounded-xl border p-6 text-sm text-red-600" style={{ borderColor: "#E8EDF5" }}>
          {error}
        </div>
      ) : (
        <div className="bg-white rounded-xl border overflow-hidden" style={{ borderColor: "#E8EDF5" }}>
          <Table
            columns={columns}
            rows={result?.data ?? []}
            rowKey={(r) => r.id}
            loading={loading}
            emptyTitle="No reconciliation runs found"
            emptyDescription="Trigger a run or adjust your filters."
            onRowClick={(r) => openRun(r.id)}
          />
          {result && result.totalPages > 1 && (
            <div className="px-5 py-3 border-t border-gray-100">
              <Pagination page={result.page + 1} totalPages={result.totalPages} onPageChange={setPage} />
            </div>
          )}
        </div>
      )}

      <Modal open={selectedRunId !== null} onClose={() => setSelectedRunId(null)} title="Reconciliation Run Detail">
        {!runDetail ? (
          <div className="flex justify-center py-8">
            <LoadingSpinner />
          </div>
        ) : (
          <div className="space-y-4">
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <span className="text-[var(--cib-text-muted)]">Institution:</span> {runDetail.institution}
              </div>
              <div>
                <span className="text-[var(--cib-text-muted)]">Date:</span> {runDetail.date}
              </div>
              <div>
                <span className="text-[var(--cib-text-muted)]">Bank Amount:</span> {money(runDetail.bankAmountEGP)}
              </div>
              <div>
                <span className="text-[var(--cib-text-muted)]">System Amount:</span> {money(runDetail.systemAmountEGP)}
              </div>
              <div>
                <Badge tone={RUN_STATUS_TONE[runDetail.status] ?? "neutral"}>{runDetail.status}</Badge>
              </div>
            </div>
            <div className="max-h-72 overflow-y-auto border border-[var(--cib-border)] rounded-md">
              <table className="w-full text-xs">
                <thead className="bg-[#F8FAFD] sticky top-0">
                  <tr>
                    <th className="text-left px-3 py-2 font-semibold text-gray-400">Ref</th>
                    <th className="text-left px-3 py-2 font-semibold text-gray-400">Student</th>
                    <th className="text-left px-3 py-2 font-semibold text-gray-400">Amount</th>
                    <th className="text-left px-3 py-2 font-semibold text-gray-400">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {(runDetail.transactions ?? []).map((t) => (
                    <tr key={t.id} className="border-t border-gray-50">
                      <td className="px-3 py-2 font-mono">{t.txRef}</td>
                      <td className="px-3 py-2">{t.studentName}</td>
                      <td className="px-3 py-2">{money(t.amountEGP)}</td>
                      <td className="px-3 py-2">{t.status}</td>
                    </tr>
                  ))}
                  {(runDetail.transactions ?? []).length === 0 && (
                    <tr>
                      <td colSpan={4} className="px-3 py-6 text-center text-gray-400">
                        No transactions recorded for this run.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Exceptions tab
// ---------------------------------------------------------------------------

interface ExceptionsPage {
  data: ReconciliationExceptionDto[];
  page: number;
  totalPages: number;
  total: number;
}

function ExceptionsTab({ onExceptionResolved }: { onExceptionResolved: () => void }) {
  const apiClient = useApiClient();
  const toast = useToast();

  const [result, setResult] = useState<ExceptionsPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loading = result === null && error === null;

  const [status, setStatus] = useState("");
  const [priority, setPriority] = useState("");
  const [includeResolved, setIncludeResolved] = useState(false);
  const [page, setPage] = useState(1);

  const [assignees, setAssignees] = useState<string[]>([]);

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ReconciliationExceptionDetailDto | null>(null);

  const [resolveOpen, setResolveOpen] = useState(false);
  const [resolveStatus, setResolveStatus] = useState("Resolved");
  const [resolutionAction, setResolutionAction] = useState("");
  const [notes, setNotes] = useState("");
  const [resolveSubmitting, setResolveSubmitting] = useState(false);
  const [resolveError, setResolveError] = useState<string | null>(null);

  const [assignOpen, setAssignOpen] = useState(false);
  const [assignTo, setAssignTo] = useState("");
  const [assignSubmitting, setAssignSubmitting] = useState(false);

  const load = useCallback(() => {
    const params = new URLSearchParams();
    if (status) params.set("status", status);
    if (priority) params.set("priority", priority);
    params.set("includeResolved", String(includeResolved));
    params.set("page", String(page - 1));
    params.set("pageSize", "20");

    apiClient
      .get<ExceptionsPage>(`/reconciliation/exceptions?${params.toString()}`)
      .then((data) => {
        setResult(data);
        setError(null);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load exceptions"));
  }, [apiClient, status, priority, includeResolved, page]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    apiClient.get<string[]>("/reconciliation/assignees").then(setAssignees).catch(() => {});
  }, [apiClient]);

  function openDetail(id: string) {
    setSelectedId(id);
    setDetail(null);
    apiClient
      .get<ReconciliationExceptionDetailDto>(`/reconciliation/exceptions/${id}`)
      .then(setDetail)
      .catch(() => setDetail(null));
  }

  function openResolve() {
    setResolveStatus("Resolved");
    setResolutionAction("");
    setNotes("");
    setResolveError(null);
    setResolveOpen(true);
  }

  async function submitResolve() {
    if (!detail) return;
    setResolveSubmitting(true);
    setResolveError(null);
    try {
      const updated = await apiClient.patch<ReconciliationExceptionDto>(`/reconciliation/exceptions/${detail.id}`, {
        status: resolveStatus,
        resolutionAction: resolutionAction.trim() || undefined,
        notes: notes.trim() || undefined,
      });
      toast.show(`Exception marked ${updated.status}`, "success");
      setResolveOpen(false);
      setSelectedId(null);
      setDetail(null);
      load();
      onExceptionResolved();
    } catch (err) {
      setResolveError(err instanceof Error ? err.message : "Failed to update exception");
    } finally {
      setResolveSubmitting(false);
    }
  }

  function openAssign() {
    setAssignTo(detail?.assignedTo ?? "");
    setAssignOpen(true);
  }

  async function submitAssign() {
    if (!detail || !assignTo.trim()) return;
    setAssignSubmitting(true);
    try {
      await apiClient.post<ReconciliationExceptionDto>(`/reconciliation/exceptions/${detail.id}/assign`, {
        assignedTo: assignTo.trim(),
      });
      toast.show(`Assigned to ${assignTo.trim()}`, "success");
      setAssignOpen(false);
      openDetail(detail.id);
      load();
    } catch (err) {
      toast.show(err instanceof Error ? err.message : "Failed to assign", "error");
    } finally {
      setAssignSubmitting(false);
    }
  }

  const columns: Column<ReconciliationExceptionDto>[] = [
    {
      key: "institution",
      header: "Institution",
      render: (r) => (
        <div>
          <div className="font-semibold">{r.institution}</div>
          <div className="text-[11px] text-[var(--cib-text-muted)] font-mono">{r.txRef}</div>
        </div>
      ),
    },
    { key: "type", header: "Type" },
    { key: "differenceEGP", header: "Difference", render: (r) => money(r.differenceEGP) },
    { key: "priority", header: "Priority", render: (r) => <Badge tone={PRIORITY_TONE[r.priority] ?? "neutral"}>{r.priority}</Badge> },
    { key: "assignedTo", header: "Assigned To", render: (r) => r.assignedTo ?? <span className="text-gray-400">Unassigned</span> },
    { key: "date", header: "Date" },
    { key: "status", header: "Status", render: (r) => <Badge tone={EXCEPTION_STATUS_TONE[r.status] ?? "neutral"}>{r.status}</Badge> },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2">
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(1);
            }}
            className="text-sm rounded-md border border-[var(--cib-border)] bg-white px-2.5 py-2"
          >
            <option value="">All statuses</option>
            <option value="Open">Open</option>
            <option value="Under Investigation">Under Investigation</option>
            <option value="Escalated">Escalated</option>
            <option value="Resolved">Resolved</option>
          </select>
          <select
            value={priority}
            onChange={(e) => {
              setPriority(e.target.value);
              setPage(1);
            }}
            className="text-sm rounded-md border border-[var(--cib-border)] bg-white px-2.5 py-2"
          >
            <option value="">All priorities</option>
            <option value="High">High</option>
            <option value="Medium">Medium</option>
            <option value="Low">Low</option>
          </select>
          <label className="flex items-center gap-1.5 text-xs text-[var(--cib-text-muted)]">
            <input
              type="checkbox"
              checked={includeResolved}
              onChange={(e) => {
                setIncludeResolved(e.target.checked);
                setPage(1);
              }}
            />
            Include resolved
          </label>
        </div>
        <a
          href={`${process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1"}/reconciliation/export?format=csv`}
          onClick={(e) => {
            e.preventDefault();
            exportCsv(apiClient);
          }}
          className="inline-flex items-center gap-1.5 text-sm font-medium border border-[var(--cib-border)] rounded-md px-3 py-2 hover:bg-gray-50"
        >
          <DownloadIcon className="w-4 h-4" />
          Export CSV
        </a>
      </div>

      {error ? (
        <div className="bg-white rounded-xl border p-6 text-sm text-red-600" style={{ borderColor: "#E8EDF5" }}>
          {error}
        </div>
      ) : (
        <div className="bg-white rounded-xl border overflow-hidden" style={{ borderColor: "#E8EDF5" }}>
          <Table
            columns={columns}
            rows={result?.data ?? []}
            rowKey={(r) => r.id}
            loading={loading}
            emptyTitle="No exceptions found"
            emptyDescription="All reconciled - nothing needs attention right now."
            onRowClick={(r) => openDetail(r.id)}
          />
          {result && result.totalPages > 1 && (
            <div className="px-5 py-3 border-t border-gray-100">
              <Pagination page={result.page + 1} totalPages={result.totalPages} onPageChange={setPage} />
            </div>
          )}
        </div>
      )}

      <Modal
        open={selectedId !== null}
        onClose={() => {
          setSelectedId(null);
          setDetail(null);
        }}
        title="Exception Detail"
        footer={
          detail && detail.status !== "Resolved" ? (
            <>
              <Button variant="secondary" size="sm" onClick={openAssign}>
                Assign
              </Button>
              <Button size="sm" onClick={openResolve}>
                Resolve
              </Button>
            </>
          ) : undefined
        }
      >
        {!detail ? (
          <div className="flex justify-center py-8">
            <LoadingSpinner />
          </div>
        ) : (
          <div className="space-y-4">
            <div className="flex items-center gap-2 flex-wrap">
              <Badge tone={EXCEPTION_STATUS_TONE[detail.status] ?? "neutral"}>{detail.status}</Badge>
              <Badge tone={PRIORITY_TONE[detail.priority] ?? "neutral"}>{detail.priority} priority</Badge>
              <span className="text-xs text-[var(--cib-text-muted)] font-mono">{detail.txRef}</span>
            </div>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <span className="text-[var(--cib-text-muted)]">Institution:</span> {detail.institution}
              </div>
              <div>
                <span className="text-[var(--cib-text-muted)]">Type:</span> {detail.type}
              </div>
              <div>
                <span className="text-[var(--cib-text-muted)]">Bank Amount:</span> {money(detail.bankAmountEGP)}
              </div>
              <div>
                <span className="text-[var(--cib-text-muted)]">System Amount:</span> {money(detail.systemAmountEGP)}
              </div>
              <div>
                <span className="text-[var(--cib-text-muted)]">Difference:</span> {money(detail.differenceEGP)}
              </div>
              <div>
                <span className="text-[var(--cib-text-muted)]">Assigned To:</span> {detail.assignedTo ?? "Unassigned"}
              </div>
            </div>
            {detail.reason && (
              <p className="text-xs text-[var(--cib-text)] bg-[#F8FAFD] rounded px-3 py-2">{detail.reason}</p>
            )}

            {detail.comparisonRows && detail.comparisonRows.length > 0 && (
              <div>
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Source Comparison</p>
                <div className="border border-[var(--cib-border)] rounded-md overflow-hidden">
                  <table className="w-full text-xs">
                    <thead className="bg-[#F8FAFD]">
                      <tr>
                        <th className="text-left px-3 py-2 font-semibold text-gray-400">Source</th>
                        <th className="text-left px-3 py-2 font-semibold text-gray-400">Reference</th>
                        <th className="text-left px-3 py-2 font-semibold text-gray-400">Amount</th>
                        <th className="text-left px-3 py-2 font-semibold text-gray-400">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.comparisonRows.map((row, i) => (
                        <tr key={i} className="border-t border-gray-50">
                          <td className="px-3 py-2">{row.source}</td>
                          <td className="px-3 py-2 font-mono">{row.reference}</td>
                          <td className="px-3 py-2">{money(row.amountEGP)}</td>
                          <td className="px-3 py-2">{row.status}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

            {detail.workflow && detail.workflow.length > 0 && (
              <div>
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-1.5">Resolution Workflow</p>
                <ul className="space-y-1.5">
                  {detail.workflow.map((step, i) => (
                    <li key={i} className="flex items-center gap-2 text-xs">
                      <span
                        className={`w-4 h-4 rounded-full flex items-center justify-center text-[9px] ${
                          step.done ? "bg-green-100 text-green-700" : "bg-gray-100 text-gray-400"
                        }`}
                      >
                        {step.done ? "✓" : ""}
                      </span>
                      <span className={step.done ? "text-[var(--cib-text)]" : "text-gray-400"}>{step.step}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {detail.notes && (
              <div>
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-1">Notes</p>
                <p className="text-xs text-[var(--cib-text)]">{detail.notes}</p>
              </div>
            )}
          </div>
        )}
      </Modal>

      <Modal
        open={resolveOpen}
        onClose={() => !resolveSubmitting && setResolveOpen(false)}
        title="Resolve Exception"
        footer={
          <>
            <Button variant="secondary" size="sm" disabled={resolveSubmitting} onClick={() => setResolveOpen(false)}>
              Cancel
            </Button>
            <Button size="sm" loading={resolveSubmitting} onClick={submitResolve}>
              Confirm
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          {resolveError && <p className="text-xs text-red-600 bg-red-50 rounded px-3 py-2">{resolveError}</p>}
          <label className="block">
            <span className="text-xs font-medium text-[var(--cib-text-muted)]">Resulting Status</span>
            <select value={resolveStatus} onChange={(e) => setResolveStatus(e.target.value)} className={`${INPUT_CLASSES} mt-1`}>
              <option value="Resolved">Resolved</option>
              <option value="Under Investigation">Under Investigation</option>
              <option value="Escalated">Escalated</option>
            </select>
          </label>
          <label className="block">
            <span className="text-xs font-medium text-[var(--cib-text-muted)]">Resolution Action</span>
            <input
              value={resolutionAction}
              onChange={(e) => setResolutionAction(e.target.value)}
              placeholder="e.g. Adjusted system ledger to match bank settlement"
              className={`${INPUT_CLASSES} mt-1`}
            />
          </label>
          <label className="block">
            <span className="text-xs font-medium text-[var(--cib-text-muted)]">Notes</span>
            <textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} className={`${INPUT_CLASSES} mt-1`} />
          </label>
        </div>
      </Modal>

      <Modal
        open={assignOpen}
        onClose={() => !assignSubmitting && setAssignOpen(false)}
        title="Assign Exception"
        footer={
          <>
            <Button variant="secondary" size="sm" disabled={assignSubmitting} onClick={() => setAssignOpen(false)}>
              Cancel
            </Button>
            <Button size="sm" loading={assignSubmitting} disabled={!assignTo.trim()} onClick={submitAssign}>
              Assign
            </Button>
          </>
        }
      >
        <label className="block">
          <span className="text-xs font-medium text-[var(--cib-text-muted)]">Assignee</span>
          <input
            list="assignees-list"
            value={assignTo}
            onChange={(e) => setAssignTo(e.target.value)}
            placeholder="Type or pick a name"
            className={`${INPUT_CLASSES} mt-1`}
          />
          <datalist id="assignees-list">
            {assignees.map((a) => (
              <option key={a} value={a} />
            ))}
          </datalist>
        </label>
      </Modal>
    </div>
  );
}

async function exportCsv(apiClient: ReturnType<typeof useApiClient>) {
  try {
    const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";
    const stored = typeof window !== "undefined" ? localStorage.getItem("tuition.auth.session") : null;
    const accessToken = stored ? (JSON.parse(stored) as { accessToken?: string }).accessToken : undefined;

    const res = await fetch(`${baseUrl}/reconciliation/export?format=csv`, {
      headers: { Authorization: `Bearer ${accessToken ?? ""}` },
    });
    if (!res.ok) throw new Error("Failed to export reconciliation report");
    const blob = await res.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `reconciliation-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
  } catch (err) {
    alert(err instanceof Error ? err.message : "Export failed");
  }
  void apiClient;
}
