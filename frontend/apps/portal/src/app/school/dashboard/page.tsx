"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useApiClient, useAuth } from "@tuition/api-client";
import {
  LoadingSpinner,
  EmptyState,
  SchoolIcon,
  UsersIcon,
  TrendUpIcon,
  TrendDownIcon,
  AlertIcon,
  BellIcon,
  DownloadIcon,
  ReportIcon,
  CreditCardIcon,
  CheckCircleIcon,
  ClockIcon,
  XCircleIcon,
  ChevronRightIcon,
} from "@tuition/ui";
import type {
  DashboardSummaryResponse,
  RecentPaymentsResponse,
  QuickLinksResponse,
  QuickLink,
} from "./types";

const STATUS_STYLE: Record<string, string> = {
  Successful: "bg-green-50 text-green-700 border-green-200",
  Pending: "bg-amber-50 text-amber-700 border-amber-200",
  Failed: "bg-red-50 text-red-700 border-red-200",
};

const UPLOAD_STATUS_STYLE: Record<string, string> = {
  "Completed": "bg-green-50 text-green-700",
  "Completed with Errors": "bg-amber-50 text-amber-700",
  "Failed": "bg-red-50 text-red-700",
  "Pending": "bg-blue-50 text-blue-700",
};

function money(n: number): string {
  return Math.round(n).toLocaleString();
}

function formatDate(isoString: string): string {
  const date = new Date(isoString);
  return date.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
}

function formatDateTime(isoString: string): string {
  const date = new Date(isoString);
  return date.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" }) +
    " at " +
    date.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
}

// Default quick links if API doesn't return them
const DEFAULT_QUICK_LINKS: QuickLink[] = [
  {
    id: "students",
    label: "Students",
    href: "/school/students",
    icon: "users",
  },
  {
    id: "fees",
    label: "Fee Management",
    href: "/school/fees",
    icon: "list",
  },
  {
    id: "upload",
    label: "Upload Fees",
    href: "/school/fee-upload",
    icon: "upload",
  },
  {
    id: "payments",
    label: "Payments",
    href: "/school/payments",
    icon: "credit-card",
  },
  {
    id: "reports",
    label: "Reports",
    href: "/school/reports",
    icon: "report",
  },
  {
    id: "notifications",
    label: "Notifications",
    href: "/school/notifications",
    icon: "bell",
  },
];

const ICON_MAP: Record<string, React.ComponentType<React.SVGProps<SVGSVGElement>>> = {
  users: UsersIcon,
  upload: DownloadIcon,
  "credit-card": CreditCardIcon,
  report: ReportIcon,
  bell: BellIcon,
  list: AlertIcon,
};

/**
 * Normalizes a single quick-link item into the shape this component needs.
 * The backend response has been observed to use inconsistent field names
 * (e.g. `url`/`path` instead of `href`, `name`/`title` instead of `label`,
 * `iconName` instead of `icon`). Any item missing a usable id/href/label
 * after trying the known alternates is dropped rather than rendered, since
 * a <Link> with an undefined href throws.
 */
function normalizeQuickLink(raw: unknown, index: number): QuickLink | null {
  if (!raw || typeof raw !== "object") return null;
  const r = raw as Record<string, unknown>;

  const href = [r.href, r.url, r.path, r.link].find(
    (v): v is string => typeof v === "string" && v.length > 0
  );
  const label = [r.label, r.title, r.name, r.text].find(
    (v): v is string => typeof v === "string" && v.length > 0
  );
  const icon = [r.icon, r.iconName, r.iconKey].find(
    (v): v is string => typeof v === "string" && v.length > 0
  ) ?? "list";
  const id = [r.id, r.key, r.slug].find(
    (v): v is string => typeof v === "string" && v.length > 0
  ) ?? `${label ?? "link"}-${index}`;
  const badgeCount =
    typeof r.badgeCount === "number"
      ? r.badgeCount
      : typeof r.count === "number"
      ? r.count
      : undefined;

  // Without a real destination and label there's nothing safe to render.
  if (!href || !label) return null;

  return { id, label, href, icon, badgeCount };
}

/**
 * Normalizes the quick-links API response into a plain QuickLink[].
 * The backend has been observed to return either:
 *   - a raw array: unknown[]
 *   - a wrapped object: { data: unknown[] }
 * and individual items may use inconsistent field names (see
 * normalizeQuickLink). Anything that can't be normalized is dropped;
 * if nothing usable comes back at all, falls back to the defaults so the
 * dashboard never renders an empty or crashing tile grid.
 */
function normalizeQuickLinks(response: unknown): QuickLink[] {
  const rawList: unknown[] = Array.isArray(response)
    ? response
    : Array.isArray((response as { data?: unknown })?.data)
    ? (response as { data: unknown[] }).data
    : [];

  const normalized = rawList
    .map((item, i) => normalizeQuickLink(item, i))
    .filter((item): item is QuickLink => item !== null);

  return normalized.length > 0 ? normalized : DEFAULT_QUICK_LINKS;
}

export default function SchoolDashboardPage() {
  const apiClient = useApiClient();
  const { user } = useAuth();

  const [summary, setSummary] = useState<DashboardSummaryResponse | null>(null);
  const [recentPayments, setRecentPayments] = useState<RecentPaymentsResponse | null>(null);
  const [quickLinks, setQuickLinks] = useState<QuickLink[]>(DEFAULT_QUICK_LINKS);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      apiClient.get<DashboardSummaryResponse>("/dashboard/summary"),
      apiClient.get<RecentPaymentsResponse>("/dashboard/recent-payments?limit=6"),
      apiClient
        .get<QuickLinksResponse>("/dashboard/quick-links")
        .catch(() => DEFAULT_QUICK_LINKS),
    ])
      .then(([s, p, q]) => {
        setSummary(s);
        setRecentPayments(p);
        // TEMP DEBUG: inspect the real shape of the quick-links response.
        // Remove this line once the backend shape is confirmed.
        console.log("quick-links raw response:", JSON.stringify(q));
        setQuickLinks(normalizeQuickLinks(q));
      })
      .catch((err) =>
        setError(err instanceof Error ? err.message : "Failed to load dashboard")
      );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (error) {
    return (
      <EmptyState
        title="Could not load the dashboard"
        description={error}
      />
    );
  }

  if (!summary || !recentPayments) {
    return (
      <div className="flex justify-center py-20">
        <LoadingSpinner />
      </div>
    );
  }

  const k = summary.kpis;
  const hour = new Date().getHours();
  const greeting =
    hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
  const firstName = user?.name?.split(" ")[0] ?? "";

  // Defensive backstop: even if state somehow ends up non-array, never let
  // the render crash — fall back to the defaults.
  const safeQuickLinks = Array.isArray(quickLinks) ? quickLinks : DEFAULT_QUICK_LINKS;

  // 4 KPI cards
  const kpis = [
    {
      title: "Total Collected",
      value: `EGP ${money(k.totalCollectedEGP.value)}`,
      sub: "Year to date",
      trend: k.totalCollectedEGP.trendPct,
      Icon: TrendUpIcon,
      color: "bg-green-50 text-green-600",
      href: "/school/payments",
    },
    {
      title: "Outstanding",
      value: `EGP ${money(k.outstandingEGP.value)}`,
      sub: "Awaiting payment",
      trend: k.outstandingEGP.trendPct,
      Icon: ClockIcon,
      color: "bg-amber-50 text-amber-600",
      href: "/school/fees",
    },
    {
      title: "Overdue",
      value: `EGP ${money(k.overdueEGP.value)}`,
      sub: `${k.overdueEGP.overdueFeeCount} fee${k.overdueEGP.overdueFeeCount !== 1 ? "s" : ""}`,
      trend: k.overdueEGP.trendPct,
      Icon: AlertIcon,
      color: "bg-red-50 text-red-600",
      href: "/school/fees",
    },
    {
      title: "Upload Status",
      value: k.feeUploadStatus.lastUploadStatus || "No uploads yet",
      sub: k.feeUploadStatus.lastUploadAt
        ? `Last: ${formatDate(k.feeUploadStatus.lastUploadAt)}`
        : "Start your first upload",
      trend: 0,
      Icon: DownloadIcon,
      color: UPLOAD_STATUS_STYLE[k.feeUploadStatus.lastUploadStatus || "Pending"] ||
        "bg-blue-50 text-blue-600",
      href: "/school/fee-upload",
      badge: k.feeUploadStatus.pendingResubmission ? "Pending Resubmission" : undefined,
    },
  ];

  return (
    <div className="space-y-6">
      {/* Greeting */}
      <div>
        <h1 className="text-[17px] font-semibold" style={{ color: "var(--cib-text)" }}>
          {greeting}, {firstName}
        </h1>
        <p className="text-sm mt-0.5" style={{ color: "var(--cib-text-muted)" }}>
          Here&apos;s your school&apos;s summary for today,{" "}
          {new Date(summary.asOf).toLocaleDateString("en-GB", {
            day: "numeric",
            month: "long",
            year: "numeric",
          })}
        </p>
      </div>

      {/* 4 KPI Cards */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {kpis.map(({ title, value, sub, trend, Icon, color, href, badge }) => (
          <Link
            key={title}
            href={href}
            className="block bg-white rounded-xl border p-4 hover:shadow-md transition-shadow"
            style={{ borderColor: "#E8EDF5" }}
          >
            <div className="flex items-start justify-between">
              <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider">
                  {title}
                </p>
                <p
                  className="text-lg font-bold mt-1 leading-none"
                  style={{ color: "var(--cib-text)" }}
                >
                  {value}
                </p>
                <p className="text-xs text-gray-400 mt-1.5 line-clamp-1">{sub}</p>
                {badge && (
                  <p className="text-[10px] font-semibold mt-2 px-2 py-1 rounded-full bg-orange-100 text-orange-700 w-fit">
                    {badge}
                  </p>
                )}
              </div>
              <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${color}`}>
                <Icon className="w-5 h-5" />
              </div>
            </div>
            {trend !== 0 && (
              <div
                className={`mt-2.5 flex items-center gap-1 text-[11px] font-medium ${
                  trend > 0 ? "text-green-600" : "text-red-500"
                }`}
              >
                {trend > 0 ? (
                  <TrendUpIcon className="w-3 h-3" />
                ) : (
                  <TrendDownIcon className="w-3 h-3" />
                )}
                <span>{Math.abs(trend).toFixed(1)}% vs yesterday</span>
              </div>
            )}
          </Link>
        ))}
      </div>

      {/* Recent Payments Table */}
      <div className="bg-white rounded-xl border overflow-hidden" style={{ borderColor: "#E8EDF5" }}>
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div className="flex items-center gap-2">
            <h3 className="text-sm font-semibold" style={{ color: "var(--cib-text)" }}>
              Recent Payments
            </h3>
            <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-blue-100 text-blue-700">
              {recentPayments.data.length} items
            </span>
          </div>
          <Link
            href="/school/payments"
            className="text-xs font-medium flex items-center gap-1 hover:underline"
            style={{ color: "var(--cib-blue)" }}
          >
            View all <ChevronRightIcon className="w-3.5 h-3.5" />
          </Link>
        </div>
        {recentPayments.data.length === 0 ? (
          <EmptyState
            title="No recent payments"
            description="Payment activity will appear here"
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F8FAFD]">
                  {[
                    "Student",
                    "Fee",
                    "Amount",
                    "Date",
                    "Status",
                    "Type",
                  ].map((h) => (
                    <th
                      key={h}
                      className="text-left px-5 py-3 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap"
                    >
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {recentPayments.data.map((payment) => (
                  <tr key={payment.id} className="hover:bg-[#F8FAFD] transition-colors">
                    <td className="px-5 py-3.5 text-xs text-gray-600 font-medium">
                      {payment.studentName}
                    </td>
                    <td className="px-5 py-3.5 text-xs text-gray-500 max-w-xs truncate">
                      {payment.feeName}
                    </td>
                    <td className="px-5 py-3.5 text-xs font-mono font-bold text-gray-800 whitespace-nowrap">
                      EGP {money(payment.amountEGP)}
                    </td>
                    <td className="px-5 py-3.5 text-xs text-gray-500 whitespace-nowrap">
                      {formatDate(payment.date)}
                    </td>
                    <td className="px-5 py-3.5 whitespace-nowrap">
                      <span
                        className={`text-[11px] font-semibold px-2.5 py-1 rounded-full border ${
                          STATUS_STYLE[payment.status] || STATUS_STYLE.Pending
                        }`}
                      >
                        {payment.status}
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-xs text-gray-600">
                      {payment.isPartial ? "Partial" : "Full"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Quick-Action Tiles */}
      <div>
        <h3
          className="text-sm font-semibold mb-3"
          style={{ color: "var(--cib-text)" }}
        >
          Quick Actions
        </h3>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {safeQuickLinks.map((link) => {
            const Icon = ICON_MAP[link.icon] || AlertIcon;
            return (
              <Link
                key={link.id}
                href={link.href}
                className="bg-white rounded-xl border p-4 hover:shadow-md transition-all hover:scale-105 flex flex-col items-center gap-2 text-center group"
                style={{ borderColor: "#E8EDF5" }}
              >
                <div className="w-10 h-10 rounded-lg bg-[var(--cib-blue)]/10 text-[var(--cib-blue)] flex items-center justify-center group-hover:bg-[var(--cib-blue)]/20 transition-colors">
                  <Icon className="w-5 h-5" />
                </div>
                <p className="text-xs font-semibold leading-tight" style={{ color: "var(--cib-text)" }}>
                  {link.label}
                </p>
                {link.badgeCount !== undefined && (
                  <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-full bg-red-100 text-red-700">
                    {link.badgeCount}
                  </span>
                )}
              </Link>
            );
          })}
        </div>
      </div>
    </div>
  );
}