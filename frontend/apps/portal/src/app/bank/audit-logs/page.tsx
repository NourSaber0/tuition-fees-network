"use client";

import { Fragment, useCallback, useEffect, useState } from "react";
import { useApiClient, type PageResponse } from "@tuition/api-client";
import { Pagination, LoadingSpinner, SearchIcon, DownloadIcon, CalendarIcon, LockIcon, XIcon } from "@tuition/ui";
import type { AuditLogDto, AuditLogStatsDto } from "./types";
import { severityClass, severityDot, roleLabel, formatTimestamp, toLocalIso } from "./badges";

function daysAgoIso(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return toLocalIso(d);
}

async function readAccessToken(): Promise<string> {
  try {
    const raw = localStorage.getItem("tuition.auth.session");
    return raw ? JSON.parse(raw).accessToken ?? "" : "";
  } catch {
    return "";
  }
}

export default function AuditLogsPage() {
  const apiClient = useApiClient();

  const defaultFrom = daysAgoIso(30);
  const defaultTo = daysAgoIso(0);

  const [entries, setEntries] = useState<AuditLogDto[]>([]);
  const [stats, setStats] = useState<AuditLogStatsDto | null>(null);
  const [roles, setRoles] = useState<string[]>([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [isExporting, setIsExporting] = useState(false);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const [search, setSearch] = useState("");
  const [filterRole, setFilterRole] = useState("All");
  const [filterSeverity, setFilterSeverity] = useState("All");
  const [dateFrom, setDateFrom] = useState(defaultFrom);
  const [dateTo, setDateTo] = useState(defaultTo);

  useEffect(() => {
    apiClient
      .get<string[]>("/audit-logs/roles")
      .then((res) => setRoles(res ?? []))
      .catch(() => {});
  }, [apiClient]);

  const loadStats = useCallback(() => {
    const params = new URLSearchParams();
    if (dateFrom) params.set("dateFrom", dateFrom);
    if (dateTo) params.set("dateTo", dateTo);
    apiClient
      .get<AuditLogStatsDto>(`/audit-logs/stats?${params.toString()}`)
      .then(setStats)
      .catch(() => {});
  }, [apiClient, dateFrom, dateTo]);

  const loadEntries = useCallback(() => {
    const params = new URLSearchParams();
    if (search.trim()) params.set("search", search.trim());
    if (filterRole !== "All") params.set("role", filterRole);
    if (filterSeverity !== "All") params.set("severity", filterSeverity);
    if (dateFrom) params.set("dateFrom", dateFrom);
    if (dateTo) params.set("dateTo", dateTo);
    params.set("page", String(page));
    params.set("pageSize", "20");

    apiClient
      .get<PageResponse<AuditLogDto>>(`/audit-logs?${params.toString()}`)
      .then((res) => {
        setEntries(res.data ?? []);
        setTotal(res.total ?? 0);
        setTotalPages(Math.max(1, res.totalPages ?? 1));
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [apiClient, search, filterRole, filterSeverity, dateFrom, dateTo, page]);

  useEffect(() => {
    loadStats();
  }, [loadStats]);

  useEffect(() => {
    loadEntries();
  }, [loadEntries]);

  const hasActiveFilters =
    search !== "" || filterRole !== "All" || filterSeverity !== "All" || dateFrom !== defaultFrom || dateTo !== defaultTo;

  const clearFilters = () => {
    setSearch("");
    setFilterRole("All");
    setFilterSeverity("All");
    setDateFrom(defaultFrom);
    setDateTo(defaultTo);
    setExpandedId(null);
    setPage(0);
  };

  const handleExport = async () => {
    setIsExporting(true);
    try {
      const params = new URLSearchParams();
      if (search.trim()) params.set("search", search.trim());
      if (filterRole !== "All") params.set("role", filterRole);
      if (filterSeverity !== "All") params.set("severity", filterSeverity);
      if (dateFrom) params.set("dateFrom", dateFrom);
      if (dateTo) params.set("dateTo", dateTo);

      const accessToken = await readAccessToken();
      const res = await fetch(`http://localhost:8080/api/v1/audit-logs/export?${params.toString()}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (!res.ok) throw new Error("Failed to export audit log");
      const blob = await res.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "audit-logs.csv";
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

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3 flex-wrap">
        <div className="relative flex-1 min-w-[200px] max-w-xs">
          <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
          <input
            type="text"
            placeholder="User, action, entity or ID…"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            className="w-full pl-8 pr-4 py-2 border border-[#DDE3EF] rounded-lg text-sm placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[#003087] bg-white"
          />
        </div>

        <select
          value={filterRole}
          onChange={(e) => {
            setFilterRole(e.target.value);
            setPage(0);
          }}
          className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white"
        >
          <option>All</option>
          {roles.map((r) => (
            <option key={r} value={r}>
              {roleLabel(r)}
            </option>
          ))}
        </select>

        <select
          value={filterSeverity}
          onChange={(e) => {
            setFilterSeverity(e.target.value);
            setPage(0);
          }}
          className="border border-[#DDE3EF] rounded-lg px-3 py-2 text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white"
        >
          <option value="All">All Severity</option>
          <option value="critical">Critical</option>
          <option value="warning">Warning</option>
          <option value="info">Info</option>
        </select>

        <div className="flex items-center gap-1.5">
          <div className="relative">
            <CalendarIcon className="absolute left-2.5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
            <input
              type="date"
              value={dateFrom}
              onChange={(e) => {
                setDateFrom(e.target.value);
                setPage(0);
              }}
              className="pl-8 pr-2 py-2 border border-[#DDE3EF] rounded-lg text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white w-[130px]"
            />
          </div>
          <span className="text-gray-300 text-xs">—</span>
          <input
            type="date"
            value={dateTo}
            onChange={(e) => {
              setDateTo(e.target.value);
              setPage(0);
            }}
            className="px-2 py-2 border border-[#DDE3EF] rounded-lg text-sm text-gray-600 focus:outline-none focus:ring-2 focus:ring-blue-200 bg-white w-[130px]"
          />
        </div>

        {hasActiveFilters && (
          <button
            onClick={clearFilters}
            className="flex items-center gap-1 text-xs font-semibold text-gray-400 hover:text-red-500 border border-gray-200 rounded-lg px-2.5 py-2 hover:bg-gray-50 transition-colors"
          >
            <XIcon className="w-3 h-3" /> Clear
          </button>
        )}

        <button
          onClick={handleExport}
          disabled={isExporting}
          className="flex items-center gap-1.5 px-3 py-2 text-sm font-semibold border border-[#DDE3EF] rounded-lg bg-white text-gray-500 hover:bg-gray-50 transition-colors ml-auto disabled:opacity-50"
        >
          <DownloadIcon className="w-4 h-4" /> {isExporting ? "Exporting…" : "Export Log"}
        </button>
      </div>

      {hasActiveFilters && (
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-[11px] text-gray-400 font-semibold">Filters:</span>
          {search && (
            <span className="text-[11px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">&quot;{search}&quot;</span>
          )}
          {filterRole !== "All" && (
            <span className="text-[11px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">{roleLabel(filterRole)}</span>
          )}
          {filterSeverity !== "All" && (
            <span className="text-[11px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold capitalize">{filterSeverity}</span>
          )}
          {(dateFrom !== defaultFrom || dateTo !== defaultTo) && (
            <span className="text-[11px] bg-[#003087]/10 text-[#003087] px-2 py-0.5 rounded-full font-semibold">
              {dateFrom} – {dateTo}
            </span>
          )}
        </div>
      )}

      <div className="flex items-center gap-4 bg-white rounded-xl border border-[#E8EDF5] px-5 py-3">
        {(["critical", "warning", "info"] as const).map((sev) => (
          <div key={sev} className="flex items-center gap-2">
            <span className={`w-2 h-2 rounded-full ${severityDot(sev)}`} />
            <span className="text-xs text-gray-400 capitalize">{sev}:</span>
            <span className="text-xs font-bold text-gray-700">{stats?.[sev] ?? 0}</span>
          </div>
        ))}
        <div className="h-4 w-px bg-gray-100 mx-1" />
        <span className="text-xs text-gray-400">
          Total (in range): <strong className="text-gray-700">{stats?.total ?? 0}</strong>
        </span>
        <span className="text-xs text-gray-400">
          Showing: <strong className="text-gray-700">{total}</strong>
        </span>
      </div>

      <div className="bg-white rounded-xl border border-[#E8EDF5] overflow-hidden">
        <div className="flex items-center gap-2 px-5 py-2.5 bg-[#F8FAFD] border-b border-[#E8EDF5]">
          <LockIcon className="w-3.5 h-3.5 text-gray-400 shrink-0" />
          <span className="text-[11px] font-semibold text-gray-400">
            Read-only · Audit records are tamper-evident and cannot be edited or deleted
          </span>
        </div>

        {loading ? (
          <div className="flex flex-col items-center justify-center py-16 space-y-3">
            <LoadingSpinner />
            <p className="text-sm text-gray-500">Loading audit log...</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F8FAFD] border-b border-[#E8EDF5]">
                  {["Severity", "User", "Action", "Entity", "Previous Value", "New Value", "Timestamp", "IP"].map((h) => (
                    <th key={h} className="text-left px-4 py-3.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {entries.length === 0 ? (
                  <tr>
                    <td colSpan={8} className="px-5 py-10 text-center text-xs text-gray-400">
                      No audit entries match the current filters.
                    </td>
                  </tr>
                ) : (
                  entries.map((entry) => (
                    <Fragment key={entry.id}>
                      <tr
                        onClick={() => setExpandedId(expandedId === entry.id ? null : entry.id)}
                        className="hover:bg-[#F8FAFD] transition-colors cursor-pointer select-none"
                      >
                        <td className="px-4 py-3.5">
                          <span className={`inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded border ${severityClass(entry.severity)}`}>
                            <span className={`w-1.5 h-1.5 rounded-full ${severityDot(entry.severity)}`} />
                            {entry.severity}
                          </span>
                        </td>
                        <td className="px-4 py-3.5">
                          <div className="font-mono text-xs font-bold text-[#003087] whitespace-nowrap">{entry.user}</div>
                          <div className="text-[10px] text-gray-400">{roleLabel(entry.role)}</div>
                        </td>
                        <td className="px-4 py-3.5 text-xs text-gray-700 whitespace-nowrap">{entry.action}</td>
                        <td className="px-4 py-3.5 text-xs text-gray-600 max-w-[150px] truncate">{entry.entity}</td>
                        <td className="px-4 py-3.5">
                          {!entry.prevValue ? (
                            <span className="text-gray-300 text-xs">—</span>
                          ) : (
                            <span className="text-xs bg-red-50 text-red-700 px-2 py-0.5 rounded font-mono">{entry.prevValue}</span>
                          )}
                        </td>
                        <td className="px-4 py-3.5">
                          {!entry.newValue ? (
                            <span className="text-gray-300 text-xs">—</span>
                          ) : (
                            <span className="text-xs bg-green-50 text-green-700 px-2 py-0.5 rounded font-mono">{entry.newValue}</span>
                          )}
                        </td>
                        <td className="px-4 py-3.5 text-xs text-gray-400 whitespace-nowrap font-mono">{formatTimestamp(entry.timestamp)}</td>
                        <td className="px-4 py-3.5 text-xs font-mono text-gray-400">{entry.ipAddress}</td>
                      </tr>

                      {expandedId === entry.id && (
                        <tr className="bg-[#F4F6F9]">
                          <td colSpan={8} className="px-5 py-3.5 border-t border-[#E8EDF5]">
                            <div className="grid grid-cols-4 gap-x-8 gap-y-2 text-xs">
                              <div>
                                <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider block mb-0.5">Entry ID</span>
                                <span className="font-mono text-gray-700">{entry.id}</span>
                              </div>
                              <div>
                                <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider block mb-0.5">Entity ID</span>
                                <span className="font-mono text-[#003087] font-semibold">{entry.entityId}</span>
                              </div>
                              <div>
                                <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider block mb-0.5">Full Timestamp</span>
                                <span className="font-mono text-gray-700">{formatTimestamp(entry.timestamp)}</span>
                              </div>
                              <div>
                                <span className="text-[10px] font-semibold text-gray-400 uppercase tracking-wider block mb-0.5">Session IP</span>
                                <span className="font-mono text-gray-700">{entry.ipAddress}</span>
                              </div>
                            </div>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        <div className="flex items-center justify-between px-5 py-3.5 border-t border-gray-100">
          <div className="flex items-center gap-2">
            <LockIcon className="w-3 h-3 text-gray-300" />
            <span className="text-xs text-gray-400">
              Showing {entries.length} of {total} entries
            </span>
          </div>
          <Pagination page={page + 1} totalPages={totalPages} onPageChange={(p) => setPage(p - 1)} />
        </div>
      </div>
    </div>
  );
}
