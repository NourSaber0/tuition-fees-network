"use client";

import { useCallback, useEffect, useState } from "react";
import { useApiClient, type PageResponse } from "@tuition/api-client";
import {
  Button,
  LoadingSpinner,
  Pagination,
  DownloadIcon,
  CalendarIcon,
  CheckCircleIcon,
  ReportIcon,
  XIcon,
  AlertIcon,
} from "@tuition/ui";
import type {
  ReportCatalogueEntry,
  GenerateReportRequest,
  ReportJobResponse,
  ReportHistoryEntry,
  InstitutionOption,
} from "./types";
import { categoryClass, previewStatusClass, friendlyGenerateError } from "./badges";

const FEE_TYPES = ["Tuition", "Bus subscription", "Books & materials", "Activities"];
const PAY_STATUSES = ["Successful", "Pending", "Failed", "Refunded"];
const PAY_METHODS = ["CIB Account", "Credit Card", "EPP"];
const EPP_TENORS = [3, 6, 12, 18];
const EPP_STATUSES = ["Active", "Completed", "Defaulted", "Cancelled"];

/** Formats using local date components - toISOString() would convert to UTC and can roll the date back a day in positive-offset timezones. */
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

async function readAccessToken(): Promise<string> {
  try {
    const raw = localStorage.getItem("tuition.auth.session");
    return raw ? JSON.parse(raw).accessToken ?? "" : "";
  } catch {
    return "";
  }
}

async function downloadReportFile(jobId: string, filename: string) {
  const accessToken = await readAccessToken();
  const res = await fetch(`http://localhost:8080/api/v1/reports/jobs/${jobId}/download`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
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

function formatDateTime(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return (
    d.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" }) +
    " " +
    d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })
  );
}

export default function ReportsPage() {
  const apiClient = useApiClient();

  const [catalogue, setCatalogue] = useState<ReportCatalogueEntry[]>([]);
  const [institutions, setInstitutions] = useState<InstitutionOption[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterCat, setFilterCat] = useState("All");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [dateFrom, setDateFrom] = useState(firstOfMonthIso());
  const [dateTo, setDateTo] = useState(todayIso());
  const [singleDate, setSingleDate] = useState(todayIso());
  const [institutionId, setInstitutionId] = useState("");
  const [feeType, setFeeType] = useState("");
  const [paymentStatus, setPaymentStatus] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("");
  const [eppTenor, setEppTenor] = useState("");
  const [eppStatus, setEppStatus] = useState("");
  const [reconStatus, setReconStatus] = useState("");
  const [format, setFormat] = useState("CSV");

  const [generating, setGenerating] = useState(false);
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [job, setJob] = useState<ReportJobResponse | null>(null);
  const [isDownloading, setIsDownloading] = useState(false);

  const [history, setHistory] = useState<ReportHistoryEntry[]>([]);
  const [historyTotalPages, setHistoryTotalPages] = useState(1);
  const [historyPage, setHistoryPage] = useState(0);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  useEffect(() => {
    Promise.all([
      apiClient.get<ReportCatalogueEntry[]>("/reports/catalogue"),
      apiClient.get<PageResponse<InstitutionOption>>("/institutions?page=0&pageSize=100"),
    ])
      .then(([catalogueRes, instRes]) => {
        setCatalogue(catalogueRes ?? []);
        setInstitutions(instRes.data ?? []);
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [apiClient]);

  const loadHistory = useCallback(() => {
    apiClient
      .get<PageResponse<ReportHistoryEntry>>(`/reports/history?page=${historyPage}&pageSize=8`)
      .then((res) => {
        setHistory(res.data ?? []);
        setHistoryTotalPages(Math.max(1, res.totalPages ?? 1));
      })
      .catch(() => {});
  }, [apiClient, historyPage]);

  useEffect(() => {
    loadHistory();
  }, [loadHistory, refreshTrigger]);

  const selectedReport = catalogue.find((r) => r.id === selectedId) ?? null;
  const categories = ["All", ...Array.from(new Set(catalogue.map((r) => r.category)))];
  const filtered = catalogue.filter((r) => filterCat === "All" || r.category === filterCat);
  const ctxFilters = selectedReport?.contextFilters ?? [];
  const dateError = selectedReport && !selectedReport.singleDate && dateFrom > dateTo;

  const handleSelect = (entry: ReportCatalogueEntry) => {
    setSelectedId(entry.id);
    setJob(null);
    setGenerateError(null);
    if (!entry.formats.includes(format)) {
      setFormat(entry.formats.includes("CSV") ? "CSV" : entry.formats[0]);
    }
  };

  const handleGenerate = async () => {
    if (!selectedReport) return;
    if (dateError) {
      setGenerateError("The start date must be before the end date.");
      return;
    }
    setGenerating(true);
    setGenerateError(null);
    try {
      const payload: GenerateReportRequest = {
        reportId: selectedReport.id,
        format,
        filters: {
          institutionId: institutionId || undefined,
          feeType: feeType || undefined,
          paymentStatus: paymentStatus || undefined,
          paymentMethod: paymentMethod || undefined,
          eppTenor: eppTenor ? Number(eppTenor) : undefined,
          eppStatus: eppStatus || undefined,
          reconStatus: reconStatus || undefined,
        },
      };
      if (selectedReport.singleDate) {
        payload.date = singleDate;
      } else {
        payload.dateFrom = dateFrom;
        payload.dateTo = dateTo;
      }
      const res = await apiClient.post<ReportJobResponse>("/reports/generate", payload);
      setJob(res);
      setRefreshTrigger((p) => p + 1);
    } catch (err) {
      setGenerateError(err instanceof Error ? friendlyGenerateError(err.message) : "Failed to generate report.");
    } finally {
      setGenerating(false);
    }
  };

  const handleDownload = async (jobId: string, filename: string) => {
    setIsDownloading(true);
    try {
      await downloadReportFile(jobId, filename);
    } catch (err) {
      alert(err instanceof Error ? err.message : "Download failed");
    } finally {
      setIsDownloading(false);
    }
  };

  const clearFilters = () => {
    setDateFrom(firstOfMonthIso());
    setDateTo(todayIso());
    setSingleDate(todayIso());
    setInstitutionId("");
    setFeeType("");
    setPaymentStatus("");
    setPaymentMethod("");
    setEppTenor("");
    setEppStatus("");
    setReconStatus("");
    setJob(null);
    setGenerateError(null);
  };

  const activeFilterCount = [institutionId, feeType, paymentStatus, paymentMethod, eppTenor, eppStatus, reconStatus].filter(
    Boolean
  ).length;

  const institutionLabel = institutions.find((i) => i.id === institutionId)?.name ?? "";

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center py-24 space-y-3">
        <LoadingSpinner />
        <p className="text-sm text-gray-500">Loading reports catalogue...</p>
      </div>
    );
  }

  return (
    <div className="space-y-4 pb-12">
      <div>
        <h1 className="text-xl font-bold tracking-tight" style={{ color: "var(--cib-blue)" }}>
          Reports
        </h1>
        <p className="text-xs text-gray-500 mt-1">
          Generate and download operational reports across the network.
        </p>
      </div>

      <div className="flex items-center gap-2 flex-wrap">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setFilterCat(cat)}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              filterCat === cat ? "bg-[#003087] text-white" : "bg-white border border-[#DDE3EF] text-gray-500 hover:bg-gray-50"
            }`}
          >
            {cat}
            {cat !== "All" && (
              <span className="ml-1 opacity-60">({catalogue.filter((r) => r.category === cat).length})</span>
            )}
          </button>
        ))}
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="col-span-2 space-y-2">
          {filtered.map((report) => (
            <div
              key={report.id}
              onClick={() => handleSelect(report)}
              className={`bg-white rounded-xl border p-4 cursor-pointer transition-all ${
                selectedId === report.id ? "border-[#003087] ring-1 ring-blue-200" : "border-[#E8EDF5] hover:border-[#003087]/30"
              } ${!report.available ? "opacity-60" : ""}`}
            >
              <div className="flex items-start justify-between">
                <div className="flex items-start gap-3">
                  <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${categoryClass(report.category)}`}>
                    <ReportIcon className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="text-sm font-semibold text-gray-800">{report.title}</span>
                      <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${categoryClass(report.category)}`}>
                        {report.category}
                      </span>
                      {report.singleDate && (
                        <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-gray-100 text-gray-400">
                          Single Date
                        </span>
                      )}
                      {!report.available && (
                        <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-red-50 text-red-600">
                          Unavailable
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-gray-400 mt-0.5">
                      {report.available ? report.description : report.unavailableReason ?? report.description}
                    </p>
                    <div className="flex items-center gap-2 mt-1.5">
                      {report.formats.map((f) => (
                        <span key={f} className="text-[10px] font-semibold text-gray-400 border border-gray-200 rounded px-1.5 py-0.5">
                          {f}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
                <div className="text-right shrink-0 ml-3">
                  <div className="text-[10px] text-gray-400">Last generated</div>
                  <div className="text-[11px] font-semibold text-gray-600 whitespace-nowrap">
                    {formatDateTime(report.lastGeneratedAt)}
                  </div>
                </div>
              </div>
            </div>
          ))}

          {history.length > 0 && (
            <div className="bg-white rounded-xl border border-[#E8EDF5] mt-4">
              <div className="px-5 py-3.5 border-b border-gray-100">
                <h3 className="text-sm font-semibold text-[#1B2A4A]">Recent Reports</h3>
              </div>
              <div className="divide-y divide-gray-50">
                {history.map((h) => (
                  <div key={h.jobId} className="flex items-center justify-between px-5 py-3">
                    <div className="min-w-0">
                      <div className="text-xs font-mono text-gray-700 truncate">{h.filename}</div>
                      <div className="text-[11px] text-gray-400 mt-0.5">
                        {formatDateTime(h.createdAt)} · {h.rowCount} rows
                      </div>
                    </div>
                    <button
                      onClick={() => handleDownload(h.jobId, h.filename)}
                      disabled={isDownloading}
                      className="p-1.5 rounded hover:bg-[#003087]/10 text-[#003087] transition-colors shrink-0 disabled:opacity-40"
                    >
                      <DownloadIcon className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ))}
              </div>
              <div className="px-5 py-3 border-t border-gray-100">
                <Pagination page={historyPage + 1} totalPages={historyTotalPages} onPageChange={(p) => setHistoryPage(p - 1)} />
              </div>
            </div>
          )}
        </div>

        <div className="space-y-3">
          <div className="bg-white rounded-xl border border-[#E8EDF5] p-5">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-semibold text-[#1B2A4A]">Generate Report</h3>
              {activeFilterCount > 0 && (
                <button onClick={clearFilters} className="flex items-center gap-1 text-[11px] font-semibold text-gray-400 hover:text-red-500 transition-colors">
                  <XIcon className="w-3 h-3" /> Clear
                  <span className="ml-0.5 bg-[#003087] text-white text-[10px] px-1.5 py-0.5 rounded-full">{activeFilterCount}</span>
                </button>
              )}
            </div>

            {!selectedReport ? (
              <div className="text-center py-8 text-gray-300">
                <ReportIcon className="w-8 h-8 mx-auto mb-2 opacity-40" />
                <p className="text-xs">Select a report type to configure and generate</p>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="bg-[#F8FAFD] rounded-lg p-3">
                  <div className="text-xs font-bold text-[#1B2A4A]">{selectedReport.title}</div>
                  <div className="text-[11px] text-gray-400 mt-0.5">{selectedReport.description}</div>
                </div>

                {selectedReport.singleDate ? (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                      Collection Date
                    </label>
                    <div className="relative">
                      <CalendarIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
                      <input
                        type="date"
                        value={singleDate}
                        onChange={(e) => {
                          setSingleDate(e.target.value);
                          setJob(null);
                        }}
                        className="w-full pl-8 border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                      />
                    </div>
                  </div>
                ) : (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                      Date Range
                    </label>
                    <div className="grid grid-cols-2 gap-2">
                      <input
                        type="date"
                        value={dateFrom}
                        onChange={(e) => {
                          setDateFrom(e.target.value);
                          setJob(null);
                        }}
                        className={`border rounded-lg px-2.5 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] ${
                          dateError ? "border-red-400 bg-red-50" : "border-[#DDE3EF]"
                        }`}
                      />
                      <input
                        type="date"
                        value={dateTo}
                        onChange={(e) => {
                          setDateTo(e.target.value);
                          setJob(null);
                        }}
                        className={`border rounded-lg px-2.5 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] ${
                          dateError ? "border-red-400 bg-red-50" : "border-[#DDE3EF]"
                        }`}
                      />
                    </div>
                    {dateError && (
                      <div className="flex items-center gap-1.5 mt-1.5 text-[11px] text-red-500">
                        <AlertIcon className="w-3 h-3" /> &quot;From&quot; date must be before &quot;To&quot; date.
                      </div>
                    )}
                  </div>
                )}

                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                    Institution
                  </label>
                  <select
                    value={institutionId}
                    onChange={(e) => {
                      setInstitutionId(e.target.value);
                      setJob(null);
                    }}
                    className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                  >
                    <option value="">All Institutions (Network)</option>
                    {institutions.map((inst) => (
                      <option key={inst.id} value={inst.id}>
                        {inst.name}
                      </option>
                    ))}
                  </select>
                </div>

                {ctxFilters.includes("feeType") && (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                      Fee Type
                    </label>
                    <select
                      value={feeType}
                      onChange={(e) => {
                        setFeeType(e.target.value);
                        setJob(null);
                      }}
                      className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                    >
                      <option value="">All Fee Types</option>
                      {FEE_TYPES.map((f) => (
                        <option key={f}>{f}</option>
                      ))}
                    </select>
                  </div>
                )}

                {ctxFilters.includes("paymentStatus") && (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                      Payment Status
                    </label>
                    <select
                      value={paymentStatus}
                      onChange={(e) => {
                        setPaymentStatus(e.target.value);
                        setJob(null);
                      }}
                      className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                    >
                      <option value="">All Statuses</option>
                      {PAY_STATUSES.map((s) => (
                        <option key={s}>{s}</option>
                      ))}
                    </select>
                  </div>
                )}

                {ctxFilters.includes("paymentMethod") && (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                      Payment Method
                    </label>
                    <select
                      value={paymentMethod}
                      onChange={(e) => {
                        setPaymentMethod(e.target.value);
                        setJob(null);
                      }}
                      className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                    >
                      <option value="">All Methods</option>
                      {PAY_METHODS.map((m) => (
                        <option key={m}>{m}</option>
                      ))}
                    </select>
                  </div>
                )}

                {ctxFilters.includes("eppTenor") && (
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                        Tenor
                      </label>
                      <select
                        value={eppTenor}
                        onChange={(e) => {
                          setEppTenor(e.target.value);
                          setJob(null);
                        }}
                        className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                      >
                        <option value="">All Tenors</option>
                        {EPP_TENORS.map((t) => (
                          <option key={t} value={t}>
                            {t} Months
                          </option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                        EPP Status
                      </label>
                      <select
                        value={eppStatus}
                        onChange={(e) => {
                          setEppStatus(e.target.value);
                          setJob(null);
                        }}
                        className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                      >
                        <option value="">All Statuses</option>
                        {EPP_STATUSES.map((s) => (
                          <option key={s}>{s}</option>
                        ))}
                      </select>
                    </div>
                  </div>
                )}

                {ctxFilters.includes("reconStatus") && (
                  <div>
                    <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                      Reconciliation Status
                    </label>
                    <input
                      type="text"
                      value={reconStatus}
                      onChange={(e) => {
                        setReconStatus(e.target.value);
                        setJob(null);
                      }}
                      placeholder="e.g. Matched, Exception…"
                      className="w-full border border-[#DDE3EF] rounded-lg px-3 py-2 text-xs text-gray-700 placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087]"
                    />
                  </div>
                )}

                <div>
                  <label className="block text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1.5">
                    Export Format
                  </label>
                  <div className="flex gap-2">
                    {selectedReport.formats.map((f) => (
                      <button
                        key={f}
                        onClick={() => {
                          setFormat(f);
                          setJob(null);
                        }}
                        className={`flex-1 py-2 text-xs font-bold rounded-lg border transition-all ${
                          format === f ? "bg-[#003087] text-white border-[#003087]" : "border-[#DDE3EF] text-gray-500 hover:border-[#003087]/40"
                        }`}
                      >
                        {f}
                      </button>
                    ))}
                  </div>
                  {format !== "CSV" && (
                    <p className="text-[11px] text-amber-600 mt-1.5">
                      Only CSV generation is currently wired up on the backend for this report.
                    </p>
                  )}
                </div>

                {activeFilterCount > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {institutionLabel && (
                      <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{institutionLabel}</span>
                    )}
                    {feeType && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{feeType}</span>}
                    {paymentStatus && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{paymentStatus}</span>}
                    {paymentMethod && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{paymentMethod}</span>}
                    {eppTenor && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{eppTenor} Months</span>}
                    {eppStatus && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{eppStatus}</span>}
                    {reconStatus && <span className="text-[10px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{reconStatus}</span>}
                  </div>
                )}

                {generateError && (
                  <div className="flex items-start gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700">
                    <AlertIcon className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                    {generateError}
                  </div>
                )}

                {job ? (
                  <div className="flex items-center gap-2 bg-green-50 border border-green-200 rounded-lg px-3 py-2.5">
                    <CheckCircleIcon className="w-4 h-4 text-green-600 shrink-0" />
                    <div className="flex-1 min-w-0">
                      <div className="text-xs font-semibold text-green-800">Report ready</div>
                      <div className="text-[11px] text-green-600 truncate">{job.filename}</div>
                    </div>
                    <button
                      onClick={() => handleDownload(job.jobId, job.filename)}
                      disabled={isDownloading}
                      className="p-1.5 bg-green-600 rounded text-white hover:bg-green-700 transition-colors shrink-0 disabled:opacity-50"
                    >
                      <DownloadIcon className="w-3.5 h-3.5" />
                    </button>
                  </div>
                ) : (
                  <Button
                    variant="primary"
                    onClick={handleGenerate}
                    disabled={generating || !!dateError || !selectedReport.available}
                    className="w-full justify-center py-2.5"
                  >
                    {generating ? (
                      <>
                        <span className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" /> Generating…
                      </>
                    ) : (
                      <>
                        <DownloadIcon className="w-4 h-4" /> Generate &amp; Download
                      </>
                    )}
                  </Button>
                )}
              </div>
            )}
          </div>
        </div>
      </div>

      {job?.preview && (
        <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
          <div className="flex items-center justify-between px-5 py-3.5 border-b border-gray-100">
            <div>
              <h3 className="text-sm font-semibold text-[#1B2A4A]">{selectedReport?.title} — Preview</h3>
              <p className="text-[11px] text-gray-400 mt-0.5">
                {job.preview.note} · Showing first {job.preview.rows.length} records
              </p>
            </div>
            <button
              onClick={() => handleDownload(job.jobId, job.filename)}
              disabled={isDownloading}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold text-gray-500 hover:bg-gray-50 border border-gray-200 transition-colors disabled:opacity-40"
            >
              <DownloadIcon className="w-3.5 h-3.5" /> Download Full Report
            </button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="bg-[#F8FAFD]">
                  {job.preview.columns.map((col) => (
                    <th
                      key={col.key}
                      className={`px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap ${
                        col.align === "right" ? "text-right" : col.align === "center" ? "text-center" : "text-left"
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
                    {job.preview.columns.map((col) => {
                      const val = String(row[col.key] ?? "—");
                      const isStatus = col.key === "status";
                      return (
                        <td
                          key={col.key}
                          className={`px-5 py-3.5 whitespace-nowrap ${
                            col.align === "right" ? "text-right font-mono" : col.align === "center" ? "text-center" : ""
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
                    <td colSpan={job.preview.columns.length} className="px-5 py-3 text-[11px] font-semibold text-gray-500">
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
