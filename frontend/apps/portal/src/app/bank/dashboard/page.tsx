"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useApiClient, useAuth } from "@tuition/api-client";
import {
  LoadingSpinner,
  EmptyState,
  PRIORITY_BADGE_CLASSES,
  dueDateLabel,
  formatIsoDate,
  SchoolIcon,
  UsersIcon,
  TransactionIcon,
  TrendUpIcon,
  TrendDownIcon,
  CheckCircleIcon,
  XCircleIcon,
  ClockIcon,
  ReconcileIcon,
  EPPIcon,
  ChevronRightIcon,
  AlertIcon,
} from "@tuition/ui";
import type {
  DashboardSummaryResponse,
  WeeklyCollectionsResponse,
  InstitutionStatusResponse,
  RecentTransactionsResponse,
  DeadlineSummaryResponse,
} from "./types";

const STATUS_STYLE: Record<string, string> = {
  Successful: "bg-green-50 text-green-700 border-green-200",
  Pending: "bg-amber-50 text-amber-700 border-amber-200",
  Failed: "bg-red-50 text-red-700 border-red-200",
};

/**
 * The backend's /dashboard/institution-status only ever returns these 4
 * fixed labels (see DashboardServiceImpl.getInstitutionStatus) - safe to
 * map colors by exact label, unlike the priority/status maps above which
 * key off enum-backed values.
 */
const INSTITUTION_STATUS_COLOR: Record<string, string> = {
  Active: "#22C55E",
  "Pending Approval": "#60A5FA",
  "Under Review": "#FBBF24",
  Suspended: "#F87171",
};

function money(n: number): string {
  return Math.round(n).toLocaleString();
}

/** Rounds up to a "nice" chart ceiling (1/2/2.5/5/10 x 10^n) so gridlines look clean. */
function niceCeil(max: number): number {
  if (max <= 0) return 100;
  const exp = Math.floor(Math.log10(max));
  const base = Math.pow(10, exp);
  const steps = [1, 2, 2.5, 5, 10];
  for (const s of steps) {
    if (max <= s * base) return s * base;
  }
  return 10 * base;
}

const VW = 520,
  VH = 168;
const PL = 48,
  PR = 12,
  PT = 14;
const CW = VW - PL - PR;
const CH = VH - PT - 28;
const BOTTOM = PT + CH;

export default function BankDashboardPage() {
  const apiClient = useApiClient();
  const { user } = useAuth();

  const [summary, setSummary] = useState<DashboardSummaryResponse | null>(null);
  const [weekly, setWeekly] = useState<WeeklyCollectionsResponse | null>(null);
  const [institutionStatus, setInstitutionStatus] = useState<InstitutionStatusResponse | null>(null);
  const [recentTx, setRecentTx] = useState<RecentTransactionsResponse | null>(null);
  const [deadlines, setDeadlines] = useState<DeadlineSummaryResponse | null>(null);
  const [pendingExceptions, setPendingExceptions] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [hoveredDay, setHoveredDay] = useState<number | null>(null);

  useEffect(() => {
    Promise.all([
      apiClient.get<DashboardSummaryResponse>("/dashboard/summary"),
      apiClient.get<WeeklyCollectionsResponse>("/dashboard/collections/weekly"),
      apiClient.get<InstitutionStatusResponse>("/dashboard/institution-status"),
      apiClient.get<RecentTransactionsResponse>("/dashboard/recent-transactions?limit=6"),
      apiClient.get<DeadlineSummaryResponse>("/dashboard/deadline-summary"),
      apiClient.get<{ pendingExceptions: number }>("/reconciliation/summary"),
    ])
      .then(([s, w, i, r, d, recon]) => {
        setSummary(s);
        setWeekly(w);
        setInstitutionStatus(i);
        setRecentTx(r);
        setDeadlines(d);
        setPendingExceptions(recon.pendingExceptions);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load dashboard"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (error) {
    return <EmptyState title="Could not load the dashboard" description={error} />;
  }

  if (!summary || !weekly || !institutionStatus || !recentTx || !deadlines) {
    return (
      <div className="flex justify-center py-20">
        <LoadingSpinner />
      </div>
    );
  }

  const k = summary.kpis;
  const kpis = [
    {
      title: "Active Institutions",
      value: k.activeInstitutions.value.toLocaleString(),
      sub: `${k.activeInstitutions.schools} schools · ${k.activeInstitutions.universities} universities`,
      trend: k.activeInstitutions.trendPct,
      Icon: SchoolIcon,
      color: "bg-[var(--cib-blue)]/10 text-[var(--cib-blue)]",
    },
    {
      title: "Total Students",
      value: k.totalStudents.value.toLocaleString(),
      sub: "Enrolled this year",
      trend: k.totalStudents.trendPct,
      Icon: UsersIcon,
      color: "bg-indigo-50 text-indigo-600",
    },
    {
      title: "Today's Transactions",
      value: k.todayTransactions.value.toLocaleString(),
      sub: `As of ${new Date(summary.asOf).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}`,
      trend: k.todayTransactions.trendPct,
      Icon: TransactionIcon,
      color: "bg-sky-50 text-sky-600",
    },
    {
      title: "Today's Collection",
      value: `EGP ${money(k.todayCollectionEGP.value)}`,
      sub: `vs yesterday EGP ${money(k.todayCollectionEGP.prevDayEGP)}`,
      trend: k.todayCollectionEGP.trendPct,
      Icon: TrendUpIcon,
      color: "bg-[var(--cib-orange)]/15 text-[var(--cib-orange-dark)]",
    },
    {
      title: "Successful Payments",
      value: k.successfulPayments.value.toLocaleString(),
      sub: `${k.successfulPayments.ratePct.toFixed(1)}% success rate`,
      trend: k.successfulPayments.trendPct,
      Icon: CheckCircleIcon,
      color: "bg-green-50 text-green-600",
    },
    {
      title: "Failed Payments",
      value: k.failedPayments.value.toLocaleString(),
      sub: `${k.failedPayments.ratePct.toFixed(1)}% failure rate`,
      trend: k.failedPayments.trendPct,
      Icon: XCircleIcon,
      color: "bg-red-50 text-red-500",
    },
    {
      title: "Pending Payments",
      value: k.pendingPayments.value.toLocaleString(),
      sub: "Awaiting confirmation",
      trend: k.pendingPayments.trendPct,
      Icon: ClockIcon,
      color: "bg-amber-50 text-amber-600",
    },
    {
      title: "Pending Reconciliation",
      value: k.pendingReconciliation.value.toLocaleString(),
      sub: "Exceptions flagged",
      trend: k.pendingReconciliation.trendPct,
      Icon: ReconcileIcon,
      color: "bg-orange-50 text-orange-600",
    },
    {
      title: "Active EPP Plans",
      value: k.activeEppPlans.value.toLocaleString(),
      sub: `EGP ${money(k.activeEppPlans.outstandingEGP)} outstanding`,
      trend: k.activeEppPlans.trendPct,
      Icon: EPPIcon,
      color: "bg-purple-50 text-purple-600",
    },
  ];

  const deadlineKpis = [
    { title: "Due Today", value: deadlines.dueToday, dot: "bg-orange-500" },
    { title: "Due This Week", value: deadlines.dueThisWeek, dot: "bg-amber-500" },
    { title: "Urgent", value: deadlines.urgent, dot: "bg-orange-400" },
    { title: "Overdue", value: deadlines.overdue, dot: "bg-red-500" },
    { title: "Penalties Applied", value: `EGP ${money(deadlines.penaltiesAppliedEGP)}`, dot: "bg-red-600" },
  ];

  const Y_MAX = niceCeil(Math.max(...weekly.series.map((p) => p.amountEGP), 1));
  const xOf = (i: number) => PL + (i / (weekly.series.length - 1)) * CW;
  const yOf = (v: number) => PT + CH * (1 - v / Y_MAX);
  const pts = weekly.series.map((d, i) => ({ ...d, x: xOf(i), y: yOf(d.amountEGP) }));
  const pathD = pts.reduce((acc, p, i) => {
    if (i === 0) return `M ${p.x.toFixed(1)} ${p.y.toFixed(1)}`;
    const prev = pts[i - 1];
    const mx = ((prev.x + p.x) / 2).toFixed(1);
    return `${acc} C ${mx} ${prev.y.toFixed(1)} ${mx} ${p.y.toFixed(1)} ${p.x.toFixed(1)} ${p.y.toFixed(1)}`;
  }, "");
  const areaD = `${pathD} L ${pts[pts.length - 1].x.toFixed(1)} ${BOTTOM} L ${pts[0].x.toFixed(1)} ${BOTTOM} Z`;
  const gridVals = [1, 2, 3, 4].map((i) => (Y_MAX / 4) * i);
  const hov = hoveredDay !== null ? pts[hoveredDay] : null;
  const todayIso = new Date().toISOString().slice(0, 10);
  const todayIdx = pts.findIndex((p) => p.date === todayIso);

  const tooltipTx = hov ? Math.min(Math.max(hov.x - 46, PL), VW - PR - 92) : 0;
  const tooltipTy = hov ? Math.max(hov.y - 34, PT + 2) : 0;
  const tooltipLbl = hov ? (hov.amountEGP >= 1000000 ? `EGP ${(hov.amountEGP / 1000000).toFixed(2)}M` : `EGP ${(hov.amountEGP / 1000).toFixed(0)}K`) : "";

  const hour = new Date().getHours();
  const greeting = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
  const firstName = user?.name?.split(" ")[0] ?? "";

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-[17px] font-semibold" style={{ color: "var(--cib-text)" }}>
            {greeting}, {firstName}
          </h1>
          <p className="text-sm mt-0.5" style={{ color: "var(--cib-text-muted)" }}>
            Here&apos;s a network-wide summary for today,{" "}
            {new Date(summary.asOf).toLocaleDateString("en-GB", { day: "numeric", month: "long", year: "numeric" })}
          </p>
        </div>
        {pendingExceptions > 0 && (
          <div className="flex items-center gap-2 rounded-lg px-3 py-1.5" style={{ background: "#FEF3E6", border: "1px solid #FDBA74" }}>
            <span className="w-2 h-2 rounded-full animate-pulse" style={{ background: "var(--cib-orange)" }} />
            <span className="text-xs font-semibold" style={{ color: "var(--cib-orange-dark)" }}>
              {pendingExceptions} reconciliation exception{pendingExceptions !== 1 ? "s" : ""} need attention
            </span>
            <Link href="/bank/reconciliation" style={{ color: "var(--cib-orange-dark)" }} className="transition-colors hover:opacity-70">
              <ChevronRightIcon className="w-3.5 h-3.5" />
            </Link>
          </div>
        )}
      </div>

      <div className="grid grid-cols-3 gap-4">
        {kpis.map(({ title, value, sub, trend, Icon, color }) => (
          <div key={title} className="bg-white rounded-xl border p-5 hover:shadow-md transition-shadow" style={{ borderColor: "#E8EDF5" }}>
            <div className="flex items-start justify-between">
              <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">{title}</p>
                <p className="text-[22px] font-bold mt-1 leading-none" style={{ color: "var(--cib-text)" }}>
                  {value}
                </p>
                <p className="text-xs text-gray-400 mt-1.5">{sub}</p>
              </div>
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${color}`}>
                <Icon className="w-5 h-5" />
              </div>
            </div>
            {trend !== 0 && (
              <div className={`mt-3 flex items-center gap-1 text-[11px] font-medium ${trend > 0 ? "text-green-600" : "text-red-500"}`}>
                {trend > 0 ? <TrendUpIcon className="w-3 h-3" /> : <TrendDownIcon className="w-3 h-3" />}
                <span>{Math.abs(trend).toFixed(1)}% vs yesterday</span>
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-5 gap-3">
        {deadlineKpis.map((kpi) => (
          <div key={kpi.title} className="bg-white rounded-xl border px-4 py-3.5 hover:shadow-md transition-shadow" style={{ borderColor: "#E8EDF5" }}>
            <div className="flex items-center gap-1.5 mb-1.5">
              <span className={`w-2 h-2 rounded-full shrink-0 ${kpi.dot}`} />
              <span className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider leading-none">{kpi.title}</span>
            </div>
            <div className="text-xl font-bold leading-none" style={{ color: "var(--cib-text)" }}>
              {kpi.value}
            </div>
          </div>
        ))}
      </div>

      <div className="bg-white rounded-xl border overflow-hidden" style={{ borderColor: "#E8EDF5" }}>
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-lg bg-red-50 flex items-center justify-center">
              <AlertIcon className="w-4 h-4 text-red-500" />
            </div>
            <h3 className="text-sm font-semibold" style={{ color: "var(--cib-text)" }}>
              Payment Priority - Action Required
            </h3>
            <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-red-100 text-red-700">{deadlines.priorityQueue.length} items</span>
          </div>
          <Link href="/bank/transactions" className="text-xs font-medium flex items-center gap-1 hover:underline" style={{ color: "var(--cib-blue)" }}>
            View all <ChevronRightIcon className="w-3.5 h-3.5" />
          </Link>
        </div>
        {deadlines.priorityQueue.length === 0 ? (
          <EmptyState title="Nothing overdue or urgent right now" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F8FAFD]">
                  {["Ref / Student", "Institution", "Fee", "Amount", "Due Date", "Status", "Penalty"].map((h) => (
                    <th key={h} className="text-left px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {deadlines.priorityQueue.map((item) => (
                  <tr
                    key={item.feeLineId}
                    className={`hover:bg-[#F8FAFD] transition-colors ${item.priority === "OVERDUE" ? "bg-red-50/40" : item.daysToDue === 0 ? "bg-orange-50/40" : ""}`}
                  >
                    <td className="px-5 py-3.5">
                      <div className="font-mono text-xs text-[var(--cib-blue)] font-semibold">FEE-{item.feeLineId.slice(0, 8).toUpperCase()}</div>
                      <div className="text-xs text-gray-600 mt-0.5">{item.student}</div>
                    </td>
                    <td className="px-5 py-3.5 text-xs text-gray-600 whitespace-nowrap">{item.institution}</td>
                    <td className="px-5 py-3.5 text-xs text-gray-500">{item.feeType}</td>
                    <td className="px-5 py-3.5 text-xs font-mono font-bold text-gray-800 whitespace-nowrap">EGP {money(item.outstandingEGP)}</td>
                    <td className="px-5 py-3.5 whitespace-nowrap">
                      <div className="text-xs text-gray-600 font-mono">{formatIsoDate(item.dueDate)}</div>
                      <div className={`text-[10px] mt-0.5 font-medium ${item.priority === "OVERDUE" ? "text-red-600" : "text-orange-600"}`}>
                        {dueDateLabel(item.daysToDue)}
                        {item.priority === "OVERDUE" && item.daysToDue <= -7 && " · Grace ended"}
                      </div>
                    </td>
                    <td className="px-5 py-3.5 whitespace-nowrap">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${PRIORITY_BADGE_CLASSES[item.priority]}`}>
                        {item.priority}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 whitespace-nowrap">
                      {item.penaltyEGP > 0 ? (
                        <span className="text-xs font-mono font-semibold text-red-600">+EGP {money(item.penaltyEGP)}</span>
                      ) : (
                        <span className="text-xs text-gray-300">-</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="grid grid-cols-3 gap-4">
        <div className="col-span-2 bg-white rounded-xl border p-5" style={{ borderColor: "#E8EDF5" }}>
          <div className="flex items-center justify-between mb-3">
            <div>
              <h3 className="text-sm font-semibold" style={{ color: "var(--cib-text)" }}>
                Weekly Collections
              </h3>
              <p className="text-xs text-gray-400">
                {weekly.from} - {weekly.to}
              </p>
            </div>
            <span className="text-xs font-semibold px-2 py-1 rounded" style={{ background: "var(--cib-blue-light)", color: "var(--cib-blue)" }}>
              {weekly.currency}
            </span>
          </div>

          <div onMouseLeave={() => setHoveredDay(null)}>
            <svg viewBox={`0 0 ${VW} ${VH}`} className="w-full h-auto" style={{ minHeight: 130, display: "block" }}>
              <defs>
                <linearGradient id="wc-area-grad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#003087" stopOpacity="0.18" />
                  <stop offset="75%" stopColor="#003087" stopOpacity="0.04" />
                  <stop offset="100%" stopColor="#003087" stopOpacity="0" />
                </linearGradient>
              </defs>

              {gridVals.map((v) => {
                const gy = yOf(v);
                return (
                  <g key={v}>
                    <line x1={PL} y1={gy} x2={VW - PR} y2={gy} stroke="#EEF1F7" strokeWidth="1" />
                    <text x={PL - 6} y={gy + 3.5} textAnchor="end" fontSize="9" fill="#C4CDDA">
                      {v >= 1000000 ? `${(v / 1000000).toFixed(1)}M` : `${(v / 1000).toFixed(0)}K`}
                    </text>
                  </g>
                );
              })}
              <line x1={PL} y1={BOTTOM} x2={VW - PR} y2={BOTTOM} stroke="#E8EDF5" strokeWidth="1" />
              <text x={PL - 6} y={BOTTOM + 3.5} textAnchor="end" fontSize="9" fill="#C4CDDA">
                0
              </text>

              <path d={areaD} fill="url(#wc-area-grad)" />
              <path d={pathD} fill="none" stroke="#003087" strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" />

              {hov && <line x1={hov.x} y1={PT} x2={hov.x} y2={BOTTOM} stroke="#003087" strokeWidth="1" strokeDasharray="3 3" opacity="0.3" />}

              {pts.map((p, i) => {
                const isToday = i === todayIdx;
                const isHov = hoveredDay === i;
                const show = isToday || isHov;
                const fill = isToday ? "#F7941D" : "#003087";
                return (
                  <g key={p.date}>
                    <rect x={p.x - 32} y={PT} width={64} height={CH} fill="transparent" style={{ cursor: "crosshair" }} onMouseEnter={() => setHoveredDay(i)} />
                    {show && (
                      <>
                        <circle cx={p.x} cy={p.y} r={isHov ? 9 : 7} fill={fill} fillOpacity="0.14" />
                        <circle cx={p.x} cy={p.y} r={isHov ? 4.5 : 3.5} fill={fill} stroke="white" strokeWidth="1.5" />
                      </>
                    )}
                  </g>
                );
              })}

              {pts.map((p, i) => (
                <text
                  key={`${p.date}-lbl`}
                  x={p.x}
                  y={VH - 5}
                  textAnchor="middle"
                  fontSize="10"
                  fontWeight={i === todayIdx ? "700" : "500"}
                  fill={i === todayIdx ? "#F7941D" : "#9CA3AF"}
                >
                  {p.label}
                </text>
              ))}

              {hov && (
                <g>
                  <rect x={tooltipTx} y={tooltipTy} width={92} height={24} rx="4" fill="#1B2A4A" opacity="0.9" />
                  <text x={tooltipTx + 46} y={tooltipTy + 15.5} textAnchor="middle" fontSize="10" fontWeight="600" fill="white">
                    {tooltipLbl}
                  </text>
                </g>
              )}
            </svg>
          </div>

          <div className="mt-1 pt-3 border-t border-gray-100 flex gap-6 text-xs text-gray-400">
            <span>
              Week total: <strong className="text-gray-700">EGP {money(weekly.weekTotalEGP)}</strong>
            </span>
            <span>
              Daily avg: <strong className="text-gray-700">EGP {money(weekly.dailyAvgEGP)}</strong>
            </span>
          </div>
        </div>

        <div className="bg-white rounded-xl border p-5" style={{ borderColor: "#E8EDF5" }}>
          <h3 className="text-sm font-semibold mb-1" style={{ color: "var(--cib-text)" }}>
            Institution Status
          </h3>
          <div className="flex items-center gap-3 mb-4">
            <span className="text-[11px] px-2 py-0.5 rounded-full font-semibold" style={{ background: "var(--cib-blue-light)", color: "var(--cib-blue)" }}>
              {institutionStatus.schools} Schools
            </span>
            <span className="text-[11px] px-2 py-0.5 rounded-full font-semibold" style={{ background: "var(--cib-orange-light)", color: "var(--cib-orange-dark)" }}>
              {institutionStatus.universities} Universities
            </span>
          </div>
          <div className="space-y-3">
            {institutionStatus.breakdown.map((item) => (
              <div key={item.label}>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-xs text-gray-500">{item.label}</span>
                  <span className="text-xs font-bold text-gray-700">{item.count}</span>
                </div>
                <div className="h-1.5 bg-gray-100 rounded-full overflow-hidden">
                  <div className="h-full rounded-full" style={{ width: `${item.pct}%`, background: INSTITUTION_STATUS_COLOR[item.label] ?? "var(--cib-blue)" }} />
                </div>
              </div>
            ))}
          </div>
          <Link
            href="/bank/schools"
            className="mt-4 block text-center w-full text-xs font-semibold rounded-lg py-2 transition-colors"
            style={{ color: "var(--cib-blue)", border: "1px solid rgba(0,48,135,0.18)" }}
          >
            View All Institutions
          </Link>
        </div>
      </div>

      <div className="bg-white rounded-xl border" style={{ borderColor: "#E8EDF5" }}>
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <h3 className="text-sm font-semibold" style={{ color: "var(--cib-text)" }}>
            Recent Transactions
          </h3>
          <Link href="/bank/transactions" className="text-xs font-medium flex items-center gap-1 hover:underline" style={{ color: "var(--cib-blue)" }}>
            View all <ChevronRightIcon className="w-3.5 h-3.5" />
          </Link>
        </div>
        {recentTx.data.length === 0 ? (
          <EmptyState title="No transactions yet today" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F8FAFD]">
                  {["Transaction ID", "Institution", "Student", "Fee", "Amount", "Method", "Time", "Status"].map((h) => (
                    <th key={h} className="text-left px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {recentTx.data.map((tx) => (
                  <tr key={tx.id} className="hover:bg-[#F8FAFD] transition-colors">
                    <td className="px-5 py-3.5 font-mono text-xs text-[var(--cib-blue)] font-medium whitespace-nowrap">TXN-{tx.id.slice(0, 8).toUpperCase()}</td>
                    <td className="px-5 py-3.5 text-xs text-gray-700 whitespace-nowrap">{tx.institution}</td>
                    <td className="px-5 py-3.5 text-xs text-gray-600 whitespace-nowrap">{tx.student}</td>
                    <td className="px-5 py-3.5 text-xs text-gray-500 whitespace-nowrap">{tx.feeType}</td>
                    <td className="px-5 py-3.5 text-xs font-semibold text-gray-800 whitespace-nowrap font-mono">{money(tx.amountEGP)} EGP</td>
                    <td className="px-5 py-3.5 text-xs text-gray-500 whitespace-nowrap">{tx.method}</td>
                    <td className="px-5 py-3.5 text-xs text-gray-400 whitespace-nowrap font-mono">
                      {new Date(tx.timestamp).toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })}
                    </td>
                    <td className="px-5 py-3.5 whitespace-nowrap">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${STATUS_STYLE[tx.status] ?? "bg-gray-50 text-gray-600 border-gray-200"}`}>
                        {tx.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
