"use client";

// ---------------------------------------------------------------------------
// SP-P8 – School Portal Reports
// Endpoints:
//   GET  /reports/catalogue
//   POST /reports/generate          → 202 { jobId, status: "processing" }
//   GET  /reports/jobs/{jobId}      → poll until status === "ready"
//   GET  /reports/jobs/{jobId}/download  → file stream (raw fetch)
//   GET  /reports/history?reportId=&page=
//
// School is implicitly scoped — NO school-selector, NO EPP/recon filters.
// ---------------------------------------------------------------------------

import { useCallback, useEffect, useRef, useState } from "react";
import { useApiClient, type PageResponse } from "@tuition/api-client";
import {
  Button,
  LoadingSpinner,
  Pagination,
  ReportIcon,
  DownloadIcon,
  CalendarIcon,
  CheckCircleIcon,
  XCircleIcon,
  AlertIcon,
  XIcon,
  ClockIcon,
} from "@tuition/ui";
import type {
  SchoolReportCatalogueEntry,
  SchoolGenerateReportRequest,
  SchoolReportJobResponse,
  SchoolReportHistoryEntry,
} from "./types";

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const FEE_CATEGORIES = ["Tuition", "Books", "Activity", "Bus"];
const PAY_STATUSES = ["Successful", "Pending", "Failed", "Refunded"];

const CATEGORY_COLORS: Record<string, string> = {
  Collections: "bg-blue-50 text-blue-700",
  Payments: "bg-green-50 text-green-700",
  Fees: "bg-sky-50 text-sky-700",
};

const GENERATE_ERROR_MAP: Record<string, string> = {
  unsupported_format_for_report: "Only CSV export is currently supported for this report.",
  date_from_after_date_to: "The start date must be before the end date.",
};

const POLL_INTERVAL_MS = 3000;
const POLL_MAX_ATTEMPTS = 40; // 2 minutes max

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function toLocalIso(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function todayIso(): string {
  return toLocalIso(new Date());
}

function firstOfMonthIso(): string {
  const d = new Date();
  return toLocalIso(new Date(d.getFullYear(), d.getMonth(), 1));
}

function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return (
    d.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" }) +
    " " +
    d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })
  );
}

function categoryClass(category: string): string {
  return CATEGORY_COLORS[category] ?? "bg-gray-100 text-gray-600";
}

function friendlyError(raw: string): string {
  return GENERATE_ERROR_MAP[raw.trim()] ?? raw;
}

function previewStatusClass(value: string): string {
  const v = value.toLowerCase();
  if (v === "failed" || v.includes("except") || v === "defaulted") return "text-red-600 font-semibold";
  if (v === "pending" || v === "refunded") return "text-amber-600 font-semibold";
  if (v === "successful" || v.includes("match") || v === "settled" || v === "active" || v === "completed")
    return "text-green-700 font-semibold";
  return "text-gray-700";
}

async function readAccessToken(): Promise<string> {
  try {
    const raw = localStorage.getItem("tuition.auth.session");
    return raw ? (JSON.parse(raw).accessToken ?? "") : "";
  } catch {
    return "";
  }
}

async function downloadReportFile(jobId: string, filename: string): Promise<void> {
  const token = await readAccessToken();
  const res = await fetch(
    `http://localhost:8080/api/v1/reports/jobs/${jobId}/download`,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  if (!res.ok) throw new Error("Failed to download report");
  const blob = await res.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}

// ---------------------------------------------------------------------------
// Main component
// ---------------------------------------------------------------------------

export default function SchoolReportsPage() {
  const apiClient = useApiClient();

  // Catalogue
  const [catalogue, setCatalogue] = useState<SchoolReportCatalogueEntry[]>([]);
  const [catalogueLoading, setCatalogueLoading] = useState(true);
  const [filterCat, setFilterCat] = useState("All");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  // Generate panel state
  const [dateFrom, setDateFrom] = useState(firstOfMonthIso());
  const [dateTo, setDateTo] = useState(todayIso());
  const [feeCategory, setFeeCategory] = useState("");
  const [paymentStatus, setPaymentStatus] = useState("");
  const [format, setFormat] = useState("CSV");

  // Job state
  const [generating, setGenerating] = useState(false);
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [job, setJob] = useState<SchoolReportJobResponse | null>(null);
  const [polling, setPolling] = useState(false);
  const [pollError, setPollError] = useState<string | null>(null);
  const [isDownloading, setIsDownloading] = useState(false);
  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pollAttemptsRef = useRef(0);

  // History
  const [history, setHistory] = useState<SchoolReportHistoryEntry[]>([]);
  const [historyTotalPages, setHistoryTotalPages] = useState(1);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyRefresh, setHistoryRefresh] = useState(0);

  // ---------------------------------------------------------------------------
  // Load catalogue
  // ---------------------------------------------------------------------------
  useEffect(() => {
    let ignore = false;
    setCatalogueLoading(true);
    apiClient
      .get<SchoolReportCatalogueEntry[]>("/reports/catalogue")
      .then((res) => {
        if (!ignore) setCatalogue(res ?? []);
      })
      .catch(() => {})
      .finally(() => {
        if (!ignore) setCatalogueLoading(false);
      });
    return () => { ignore = true; };
  }, [apiClient]);

  // ---------------------------------------------------------------------------
  // Load history
  // ---------------------------------------------------------------------------
  const loadHistory = useCallback(() => {
    apiClient
      .get<PageResponse<SchoolReportHistoryEntry>>(
        `/reports/history?page=${historyPage}&pageSize=8`
      )
      .then((res) => {
        setHistory(res.data ?? []);
        setHistoryTotalPages(Math.max(1, res.totalPages ?? 1));
      })
      .catch(() => {});
  }, [apiClient, historyPage]);

  useEffect(() => {
    loadHistory();
  }, [loadHistory, historyRefresh]);

  // ---------------------------------------------------------------------------
  // Polling cleanup on unmount
  // ---------------------------------------------------------------------------
  useEffect(() => {
    return () => {
      if (pollTimerRef.current) clearTimeout(pollTimerRef.current);
    };
  }, []);

  // ---------------------------------------------------------------------------
  // Derived state
  // ---------------------------------------------------------------------------
  const selectedReport = catalogue.find((r) => r.id === selectedId) ?? null;
  const categories = ["All", ...Array.from(new Set(catalogue.map((r) => r.category)))];
  const filtered = filterCat === "All" ? catalogue : catalogue.filter((r) => r.category === filterCat);
  const dateError = dateFrom && dateTo && dateFrom > dateTo;
  const activeFilterCount = [feeCategory, paymentStatus].filter(Boolean).length;

  // ---------------------------------------------------------------------------
  // Select a report card
  // ---------------------------------------------------------------------------
  function handleSelect(entry: SchoolReportCatalogueEntry) {
    setSelectedId(entry.id);
    setJob(null);
    setGenerateError(null);
    setPollError(null);
    stopPolling();
    if (!entry.formats.includes(format)) {
      setFormat(entry.formats.includes("CSV") ? "CSV" : entry.formats[0]);
    }
  }

  // ---------------------------------------------------------------------------
  // Polling
  // ---------------------------------------------------------------------------
  function stopPolling() {
    if (pollTimerRef.current) {
      clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  }

  function startPolling(jobId: string) {
    pollAttemptsRef.current = 0;
    setPolling(true);

    function poll() {
      if (pollAttemptsRef.current >= POLL_MAX_ATTEMPTS) {
        setPolling(false);
        setPollError("Report generation timed out. Please try again.");
        return;
      }
      pollAttemptsRef.current++;

      apiClient
        .get<SchoolReportJobResponse>(`/reports/jobs/${jobId}`)
        .then((res) => {
          setJob(res);
          if (res.status === "ready") {
            setPolling(false);
            setHistoryRefresh((p) => p + 1);
          } else if (res.status === "failed") {
            setPolling(false);
            setPollError("Report generation failed. Please try again.");
          } else {
            // still processing — schedule next poll
            pollTimerRef.current = setTimeout(poll, POLL_INTERVAL_MS);
          }
        })
        .catch(() => {
          // transient network error — retry
          pollTimerRef.current = setTimeout(poll, POLL_INTERVAL_MS);
        });
    }

    pollTimerRef.current = setTimeout(poll, POLL_INTERVAL_MS);
  }

  // ---------------------------------------------------------------------------
  // Generate
  // ---------------------------------------------------------------------------
  async function handleGenerate() {
    if (!selectedReport || dateError) return;
    stopPolling();
    setGenerating(true);
    setGenerateError(null);
    setPollError(null);
    setJob(null);

    try {
      const payload: SchoolGenerateReportRequest = {
        reportId: selectedReport.id,
        dateFrom,
        dateTo,
        format,
        filters: {
          feeCategory: feeCategory || undefined,
          paymentStatus: paymentStatus || undefined,
        },
      };

      const res = await apiClient.post<SchoolReportJobResponse>("/reports/generate", payload);
      setJob(res);

      if (res.status === "ready") {
        // Backend returned synchronously ready — show download immediately
        setHistoryRefresh((p) => p + 1);
      } else {
        // Async — start polling
        startPolling(res.jobId);
      }
    } catch (err) {
      setGenerateError(
        err instanceof Error ? friendlyError(err.message) : "Failed to generate report."
      );
    } finally {
      setGenerating(false);
    }
  }

  // ---------------------------------------------------------------------------
  // Download
  // ---------------------------------------------------------------------------
  async function handleDownload(jobId: string, filename: string) {
    setIsDownloading(true);
    try {
      await downloadReportFile(jobId, filename);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Download failed");
    } finally {
      setIsDownloading(false);
    }
  }

  // ---------------------------------------------------------------------------
  // Clear generate panel filters
  // ---------------------------------------------------------------------------
  function clearFilters() {
    setDateFrom(firstOfMonthIso());
    setDateTo(todayIso());
    setFeeCategory("");
    setPaymentStatus("");
    setJob(null);
    setGenerateError(null);
    setPollError(null);
    stopPolling();
    setPolling(false);
  }

  // ---------------------------------------------------------------------------
  // Render
  // ---------------------------------------------------------------------------

  if (catalogueLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-24 space-y-3">
        <LoadingSpinner size={32} />
        <p className="text-sm text-gray-500">Loading reports catalogue…</p>
      </div>
    );
  }

  return (
    <div className="space-y-4 pb-12">
      {/* Page header */}
      <div>
        <h1 className="text-xl font-bold tracking-tight" style={{ color: "var(--cib-blue)" }}>
          Reports
        </h1>
        <p className="text-xs text-gray-500 mt-1">
          Generate and download reports for your school. All reports are scoped to your institution.
        </p>
      </div>

      {/* Category filter tabs */}
      <div className="flex items-center gap-2 flex-wrap">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setFilterCat(cat)}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              filterCat === cat
                ? "bg-[#003087] text-white"
                : "bg-white border border-[#DDE3EF] text-gray-500 hover:bg-gray-50"
            }`}
          >
            {cat}
            {cat !== "All" && (
              <span className="ml-1 opacity-60">
                ({catalogue.filter((r) => r.category === cat).length})
              </span>
            )}
          </button>
        ))}
      </div>

      {/* Two-column layout: catalogue (left) + generate panel (right) */}
      <div className="grid grid-cols-3 gap-4">
        {/* ---------------------------------------------------------------- */}
        {/* LEFT — Catalogue + History                                        */}
        {/* ---------------------------------------------------------------- */}
        <div className="col-span-2 space-y-3">
          {/* Report cards */}
          {filtered.map((report) => (
            <div
              key={report.id}
              onClick={() => handleSelect(report)}
              className={`bg-white rounded-xl border p-4 cursor-pointer transition-all ${
                selectedId === report.id
                  ? "border-[#003087] ring-1 ring-blue-200"
                  : "border-[#E8EDF5] hover:border-[#003087]/30"
              }`}
            >
              <div className="flex items-start justify-between">
                <div className="flex items-start gap-3">
                  {/* Category icon chip */}
                  <div
                    className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${categoryClass(report.category)}`}
                  >
                    <ReportIcon className="w-4 h-4" />
                  </div>

                  <div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold text-gray-800">{report.title}</span>
                      <span
                        className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${categoryClass(report.category)}`}
                      >
                        {report.category}
                      </span>
                    </div>
                    {report.description && (
                      <p className="text-xs text-gray-400 mt-0.5">{report.description}</p>
                    )}
                    {/* Format chips */}
                    <div className="flex items-center gap-1.5 mt-1.5">
                      {report.formats.map((f) => (
                        <span
                          key={f}
                          className="text-[10px] font-semibold text-gray-400 border border-gray-200 rounded px-1.5 py-0.5"
                        >
                          {f}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>

                {/* Last generated */}
                <div className="text-right shrink-0 ml-3">
                  <div className="text-[10px] text-gray-400">Last generated</div>
                  <div className="text-[11px] font-semibold text-gray-600 whitespace-nowrap">
                    {formatDateTime(report.lastGeneratedAt)}
                  </div>
                </div>
              </div>
            </div>
          ))}

          {/* ---------------------------------------------------------------- */}
          {/* History                                                           */}
          {/* ---------------------------------------------------------------- */}
          {history.length > 0 && (
            <div className="bg-white rounded-xl border border-[#E8EDF5] mt-2">
              <div className="px-5 py-3.5 border-b border-gray-100">
                <h3 className="text-sm font-semibold text-[#1B2A4A]">Recent Reports</h3>
              </div>
              <div className="divide-y divide-gray-50">
                {history.map((h) => (
                  <div key={h.jobId} className="flex items-center justify-between px-5 py-3">
                    <div className="min-w-0">
                      <div className="text-xs font-mono text-gray-700 truncate">{h.filename}</div>
                      <div className="text-[11px] text-gray-400 mt-0.5">
                        {formatDateTime(h.createdAt)} · {h.rowCount.toLocaleString()} rows ·{" "}
                        <span className="uppercase text-[10px] font-bold">{h.format}</span>
                      </div>
                    </div>
                    <button
                      onClick={() => handleDownload(h.jobId, h.filename)}
                      disabled={isDownloading}
                      className="p-1.5 rounded hover:bg-[#003087]/10 text-[#003087] transition-colors shrink-0 disabled:opacity-40"
                      title="Download"
                    >
                      <DownloadIcon className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
              <div className="px-5 py-3 border-t border-gray-100">
                <Pagination
                  page={historyPage + 1}
                  totalPages={historyTotalPages}
                  onPageChange={(p) => setHistoryPage(p - 1)}
                />
              </div>
            </div>
          )}
        </div>

        {/* ---------------------------------------------------------------- */}
        {/* RIGHT — Generate Panel                                            */}
        {/* ---------------------------------------------------------------- */}
        <div>
          <div className="bg-white rounded-xl border border-[#E8EDF5] p-5 sticky top-4">
            {/* Panel header */}
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-[#1B2A4A]">Generate Report</h3>
              {activeFilterCount > 0 && (
                <button
                  onClick={clearFilters}
                  className="flex items-center gap-1 text-[11px] font-semibold text-gray-400 hover:text-red-500 transition-colors"
                >
                  <XIcon className="w-3 h-3" />
                  Clear
                  <span className="ml-0.5 bg-[#003087] text-white text-[10px] px-1.5 py-0.5 rounded-full">
                    {activeFilterCount}
                  </span>
                </button>
              )}
            </div>

            {/* Empty state — no report selected */}
            {!selectedReport ? (
              <div className="text-center py-10 text-gray-300">
                <ReportIcon className="w-8 h-8 mx-auto mb-2 opacity-40" />
                <p className="text-xs">Select a report type to configure and generate</p>
              </div>
            ) : (
              <div className="space-y-4">
                {/* Selected report info */}
                <div className="bg-[#F8FAFD] rounded-lg p-3">
                  <div className="text-xs font-bold text-[#1B2A4A]">{selectedReport.title}</div>
                  {selectedReport.description && (
                    <div className="text-[11px] text-gray-400 mt-0.5">{selectedReport.description}</div>
                  )}
                </div>

                {/* Date range */}
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                    Date Range
                  </label>
                  <div className="grid grid-cols-2 gap-2">
                    <div className="relative">
                      <CalendarIcon className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3 h-3 text-gray-300 pointer-events-none" />
                      <input
                        type="date"
                        value={dateFrom}
                        onChange={(e) => { setDateFrom(e.target.value); setJob(null); }}
                        className={`w-full pl-7 pr-2 py-2 text-xs text-gray-700 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] ${
                          dateError ? "border-red-400 bg-red-50" : "border-[#DDE3EF]"
                        }`}
                      />
                    </div>
                    <div className="relative">
                      <CalendarIcon className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3 h-3 text-gray-300 pointer-events-none" />
                      <input
                        type="date"
                        value={dateTo}
                        onChange={(e) => { setDateTo(e.target.value); setJob(null); }}
                        className={`w-full pl-7 pr-2 py-2 text-xs text-gray-700 border rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] ${
                          dateError ? "border-red-400 bg-red-50" : "border-[#DDE3EF]"
                        }`}
                      />
                    </div>
                  </div>
                  {dateError && (
                    <div className="flex items-center gap-1.5 mt-1.5 text-[11px] text-red-500">
                      <AlertIcon className="w-3 h-3" /> &quot;From&quot; date must be before &quot;To&quot; date.
                    </div>
                  )}
                </div>

                {/* Fee Category filter */}
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                    Fee Category
                  </label>
                  <select
                    value={feeCategory}
                    onChange={(e) => { setFeeCategory(e.target.value); setJob(null); }}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                  >
                    <option value="">All Categories</option>
                    {FEE_CATEGORIES.map((f) => (
                      <option key={f}>{f}</option>
                    ))}
                  </select>
                </div>

                {/* Payment Status filter */}
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                    Payment Status
                  </label>
                  <select
                    value={paymentStatus}
                    onChange={(e) => { setPaymentStatus(e.target.value); setJob(null); }}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                  >
                    <option value="">All Statuses</option>
                    {PAY_STATUSES.map((s) => (
                      <option key={s}>{s}</option>
                    ))}
                  </select>
                </div>

                {/* Active filter chips */}
                {activeFilterCount > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {feeCategory && (
                      <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">
                        {feeCategory}
                      </span>
                    )}
                    {paymentStatus && (
                      <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">
                        {paymentStatus}
                      </span>
                    )}
                  </div>
                )}

                {/* Format selector */}
                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                    Export Format
                  </label>
                  <div className="flex gap-2">
                    {selectedReport.formats.map((f) => (
                      <button
                        key={f}
                        onClick={() => { setFormat(f); setJob(null); }}
                        className={`flex-1 py-2 text-xs font-bold rounded-lg border transition-all ${
                          format === f
                            ? "bg-[#003087] text-white border-[#003087]"
                            : "border-[#DDE3EF] text-gray-500 hover:border-[#003087]/40"
                        }`}
                      >
                        {f}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Generate error */}
                {generateError && (
                  <div className="flex items-start gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
                    <AlertIcon className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                    {generateError}
                  </div>
                )}

                {/* Poll error */}
                {pollError && (
                  <div className="flex items-start gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
                    <XCircleIcon className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                    {pollError}
                  </div>
                )}

                {/* Polling indicator */}
                {polling && job && job.status !== "ready" && (
                  <div className="flex items-center gap-2 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2.5">
                    <ClockIcon className="w-4 h-4 text-amber-600 shrink-0 animate-spin" />
                    <div>
                      <div className="text-xs font-semibold text-amber-800">Generating report…</div>
                      <div className="text-[11px] text-amber-600">
                        Job {job.jobId} · This may take a moment
                      </div>
                    </div>
                  </div>
                )}

                {/* Ready — download */}
                {job && job.status === "ready" && job.filename && (
                  <div className="flex items-center gap-2 bg-green-50 border border-green-200 rounded-lg px-3 py-2.5">
                    <CheckCircleIcon className="w-4 h-4 text-green-600 shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="text-xs font-semibold text-green-800">Report ready</div>
                      <div className="text-[11px] text-green-600 truncate">{job.filename}</div>
                    </div>
                    <button
                      onClick={() => handleDownload(job.jobId, job.filename!)}
                      disabled={isDownloading}
                      className="p-1.5 bg-green-600 rounded text-white hover:bg-green-700 transition-colors shrink-0 disabled:opacity-50"
                      title="Download"
                    >
                      <DownloadIcon className="w-3.5 h-3.5" />
                    </button>
                  </div>
                )}

                {/* Generate button (shown when not yet ready) */}
                {(!job || job.status !== "ready") && (
                  <Button
                    variant="primary"
                    onClick={handleGenerate}
                    disabled={generating || polling || !!dateError}
                    className="w-full justify-center py-2.5 bg-[#003087] text-white hover:bg-[#00256b]"
                  >
                    {generating || polling ? (
                      <>
                        <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                        {generating ? "Submitting…" : "Generating…"}
                      </>
                    ) : (
                      <>
                        <DownloadIcon className="w-4 h-4" />
                        Generate &amp; Download
                      </>
                    )}
                  </Button>
                )}

                {/* Re-generate button (shown after ready, in case they want another run) */}
                {job && job.status === "ready" && (
                  <button
                    onClick={() => { setJob(null); setPollError(null); stopPolling(); setPolling(false); }}
                    className="w-full text-center text-[11px] text-gray-400 hover:text-[#003087] transition-colors"
                  >
                    Generate another report
                  </button>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ---------------------------------------------------------------- */}
      {/* Preview table (below, full-width) — shown when job has preview  */}
      {/* ---------------------------------------------------------------- */}
      {job?.preview && job.preview.columns.length > 0 && (
        <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
          <div className="flex items-center justify-between px-5 py-3.5 border-b border-gray-100">
            <div>
              <h3 className="text-sm font-semibold text-[#1B2A4A]">
                {selectedReport?.title ?? "Report"} — Preview
              </h3>
              <p className="text-[11px] text-gray-400 mt-0.5">
                {job.preview.note} · Showing first {job.preview.rows.length} records
              </p>
            </div>
            {job.filename && (
              <button
                onClick={() => handleDownload(job.jobId, job.filename!)}
                disabled={isDownloading}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-gray-500 hover:bg-gray-50 border border-gray-200 transition-colors disabled:opacity-40"
              >
                <DownloadIcon className="w-3.5 h-3.5" />
                Download Full Report
              </button>
            )}
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="bg-[#F8FAFD]">
                  {job.preview.columns.map((col) => (
                    <th
                      key={col.key}
                      className={`px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap ${
                        col.align === "right"
                          ? "text-right"
                          : col.align === "center"
                          ? "text-center"
                          : "text-left"
                      }`}
                    >
                      {col.label}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {job.preview.rows.map((row, i) => (
                  <tr key={i} className="hover:bg-[#F8FAFD] transition-colors">
                    {job.preview!.columns.map((col) => {
                      const val = String(row[col.key] ?? "—");
                      const isStatus = col.key === "status";
                      return (
                        <td
                          key={col.key}
                          className={`px-5 py-3.5 whitespace-nowrap ${
                            col.align === "right"
                              ? "text-right font-mono"
                              : col.align === "center"
                              ? "text-center"
                              : ""
                          } ${isStatus ? previewStatusClass(val) : "text-gray-700"}`}
                        >
                          {val}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
              {job.preview.total && (
                <tfoot>
                  <tr className="bg-[#F8FAFD] border-t border-[#E8EDF5]">
                    <td
                      colSpan={job.preview.columns.length}
                      className="px-5 py-3 text-[11px] font-semibold text-gray-500"
                    >
                      {job.preview.total}
                    </td>
                  </tr>
                </tfoot>
              )}
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

