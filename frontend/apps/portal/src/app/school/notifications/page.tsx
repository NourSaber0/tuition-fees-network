"use client";

import { useCallback, useEffect, useState } from "react";
import { useApiClient } from "@tuition/api-client";
import {
  Badge,
  Button,
  EmptyState,
  LoadingSpinner,
  Pagination,
  Table,
  BellIcon,
  TransactionIcon,
  DownloadIcon,
  AlertIcon,
  CheckCircleIcon,
  XIcon,
  ClockIcon,
} from "@tuition/ui";
import type { Column } from "@tuition/ui";

import type {
  SchoolNotificationItem,
  SchoolNotificationListResponse,
  UnreadCountResponse,
} from "./types";

// ---------------------------------------------------------------------------
// Constants & Helpers
// ---------------------------------------------------------------------------

const PAGE_SIZE = 10;

type ViewMode = "feed" | "reminders";

const TYPE_FILTER_TABS: { label: string; value: string }[] = [
  { label: "All Types", value: "" },
  { label: "Payments", value: "payment" },
  { label: "Fee Uploads", value: "upload" },
  { label: "Reminders", value: "reminder" },
  { label: "Penalties", value: "penalty" },
];

const REMINDER_STATUS_TABS: { label: string; value: string }[] = [
  { label: "All Reminders", value: "" },
  { label: "Scheduled", value: "Scheduled" },
  { label: "Sent", value: "Sent" },
  { label: "Failed", value: "Failed" },
];

function formatEGP(amount: number): string {
  return `EGP ${Math.round(amount).toLocaleString()}`;
}

function typeBadgeTone(type: string): "success" | "warning" | "danger" | "info" | "neutral" {
  switch (type.toLowerCase()) {
    case "payment":
      return "success";
    case "upload":
      return "info";
    case "reminder":
      return "warning";
    case "penalty":
      return "danger";
    default:
      return "neutral";
  }
}

function typeIcon(type: string) {
  switch (type.toLowerCase()) {
    case "payment":
      return TransactionIcon;
    case "upload":
      return DownloadIcon;
    case "reminder":
      return BellIcon;
    case "penalty":
      return AlertIcon;
    default:
      return BellIcon;
  }
}

function statusBadgeTone(status: string): "success" | "warning" | "danger" | "info" | "neutral" {
  switch (status) {
    case "Sent":
      return "success";
    case "Scheduled":
      return "info";
    case "Failed":
      return "danger";
    default:
      return "neutral";
  }
}

// ---------------------------------------------------------------------------
// Main Component
// ---------------------------------------------------------------------------

export default function SchoolNotificationsPage() {
  const apiClient = useApiClient();

  // Active Top View Tab
  const [activeView, setActiveView] = useState<ViewMode>("feed");

  // Feed state
  const [notifications, setNotifications] = useState<SchoolNotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [feedTotal, setFeedTotal] = useState(0);
  const [feedTotalPages, setFeedTotalPages] = useState(1);
  const [feedPage, setFeedPage] = useState(0); // 0-indexed
  const [feedTypeFilter, setFeedTypeFilter] = useState("");
  const [feedUnreadOnly, setFeedUnreadOnly] = useState(false);
  const [feedLoading, setFeedLoading] = useState(true);

  // Reminders state
  const [reminders, setReminders] = useState<SchoolNotificationItem[]>([]);
  const [remindersTotal, setRemindersTotal] = useState(0);
  const [remindersTotalPages, setRemindersTotalPages] = useState(1);
  const [remindersPage, setRemindersPage] = useState(0); // 0-indexed
  const [reminderStatusFilter, setReminderStatusFilter] = useState("");
  const [remindersLoading, setRemindersLoading] = useState(false);

  // Actions
  const [markingAllRead, setMarkingAllRead] = useState(false);
  const [refreshTrigger, setRefreshTrigger] = useState(0);

  // -------------------------------------------------------------------------
  // Fetch unread count independently for top badge
  // -------------------------------------------------------------------------
  const fetchUnreadCount = useCallback(() => {
    apiClient
      .get<UnreadCountResponse>("/notifications/unread-count")
      .then((res) => {
        setUnreadCount(res.count ?? 0);
      })
      .catch(() => {});
  }, [apiClient]);

  // -------------------------------------------------------------------------
  // Fetch Feed Notifications
  // -------------------------------------------------------------------------
  const fetchFeedNotifications = useCallback(() => {
    setFeedLoading(true);
    const params = new URLSearchParams();
    if (feedTypeFilter) params.set("type", feedTypeFilter);
    if (feedUnreadOnly) params.set("read", "false");
    params.set("page", String(feedPage));
    params.set("pageSize", String(PAGE_SIZE));

    apiClient
      .get<SchoolNotificationListResponse>(`/notifications?${params.toString()}`)
      .then((res) => {
        setNotifications(res.data ?? []);
        if (typeof res.unreadCount === "number") {
          setUnreadCount(res.unreadCount);
        }
        setFeedTotal(res.total ?? 0);
        setFeedTotalPages(Math.max(1, res.totalPages ?? 1));
      })
      .catch(() => {})
      .finally(() => setFeedLoading(false));
  }, [apiClient, feedTypeFilter, feedUnreadOnly, feedPage]);

  // -------------------------------------------------------------------------
  // Fetch Reminders
  // -------------------------------------------------------------------------
  const fetchReminders = useCallback(() => {
    setRemindersLoading(true);
    const params = new URLSearchParams();
    if (reminderStatusFilter) params.set("status", reminderStatusFilter);
    params.set("page", String(remindersPage));
    params.set("pageSize", String(PAGE_SIZE));

    apiClient
      .get<SchoolNotificationListResponse>(`/notifications/reminders?${params.toString()}`)
      .then((res) => {
        setReminders(res.data ?? []);
        setRemindersTotal(res.total ?? 0);
        setRemindersTotalPages(Math.max(1, res.totalPages ?? 1));
      })
      .catch(() => {})
      .finally(() => setRemindersLoading(false));
  }, [apiClient, reminderStatusFilter, remindersPage]);

  // Initial & refresh effect
  useEffect(() => {
    fetchUnreadCount();
    if (activeView === "feed") {
      fetchFeedNotifications();
    } else {
      fetchReminders();
    }
  }, [activeView, fetchFeedNotifications, fetchReminders, fetchUnreadCount, refreshTrigger]);

  // -------------------------------------------------------------------------
  // Notification Actions
  // -------------------------------------------------------------------------
  const handleMarkRead = async (id: string) => {
    try {
      await apiClient.post(`/notifications/${id}/read`, {});
      setRefreshTrigger((p) => p + 1);
    } catch {
      // ignore
    }
  };

  const handleMarkAllRead = async () => {
    setMarkingAllRead(true);
    try {
      await apiClient.post("/notifications/read-all", {});
      setRefreshTrigger((p) => p + 1);
    } catch {
      // ignore
    } finally {
      setMarkingAllRead(false);
    }
  };

  const handleDismiss = async (id: string) => {
    try {
      await apiClient.delete(`/notifications/${id}`);
      setRefreshTrigger((p) => p + 1);
    } catch {
      // ignore
    }
  };

  // -------------------------------------------------------------------------
  // Reminders Table Columns
  // -------------------------------------------------------------------------
  const reminderColumns: Column<SchoolNotificationItem>[] = [
    {
      key: "title",
      header: "Reminder Title",
      render: (row) => (
        <div>
          <p className="font-semibold text-gray-900 text-sm">{row.title}</p>
          <p className="text-xs text-gray-500 line-clamp-1">{row.description}</p>
        </div>
      ),
    },
    {
      key: "studentName",
      header: "Student & Fee",
      render: (row) => (
        <div>
          <p className="font-medium text-gray-900 text-sm">{row.studentName ?? "—"}</p>
          <p className="text-xs text-gray-500">
            {row.feeType ?? "Fee"}{" "}
            {row.feeAmountEGP !== undefined && row.feeAmountEGP !== null
              ? `• ${formatEGP(row.feeAmountEGP)}`
              : ""}
          </p>
        </div>
      ),
    },
    {
      key: "dueDate",
      header: "Due Date",
      render: (row) => (
        <div className="text-sm text-gray-700">
          <p>{row.dueDate ?? "—"}</p>
          {row.daysUntilDue !== undefined && row.daysUntilDue !== null && (
            <span className="text-xs text-amber-700 font-medium">
              Due in {row.daysUntilDue} day{row.daysUntilDue !== 1 ? "s" : ""}
            </span>
          )}
        </div>
      ),
    },
    {
      key: "notificationStatus",
      header: "Status",
      render: (row) => (
        <Badge tone={statusBadgeTone(row.notificationStatus ?? "")}>
          {row.notificationStatus ?? "Sent"}
        </Badge>
      ),
    },
    {
      key: "date",
      header: "Date & Time",
      render: (row) => (
        <div className="text-xs text-gray-600">
          <p>{row.date}</p>
          <p className="text-gray-400">{row.time}</p>
        </div>
      ),
    },
    {
      key: "actions",
      header: "Action",
      render: (row) => (
        <div className="flex items-center gap-2">
          {!row.read && (
            <button
              onClick={() => handleMarkRead(row.id)}
              className="text-xs text-[#003087] font-semibold hover:underline"
            >
              Mark Read
            </button>
          )}
          <button
            onClick={() => handleDismiss(row.id)}
            className="p-1 text-gray-400 hover:text-red-600 transition-colors"
            title="Dismiss"
          >
            <XIcon className="w-4 h-4" />
          </button>
        </div>
      ),
    },
  ];

  // -------------------------------------------------------------------------
  // Render Component
  // -------------------------------------------------------------------------
  return (
    <div className="space-y-6 pb-12">
      {/* ------------------------------------------------------------------ */}
      {/* Page Header & Actions                                               */}
      {/* ------------------------------------------------------------------ */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold text-gray-900">Notifications</h1>
            {unreadCount > 0 && (
              <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-red-100 text-red-700">
                {unreadCount} unread
              </span>
            )}
          </div>
          <p className="mt-1 text-sm text-gray-500">
            View automated reminders, payment updates, fee upload notifications, and system alerts.
          </p>
        </div>

        {unreadCount > 0 && (
          <Button
            variant="secondary"
            size="sm"
            onClick={handleMarkAllRead}
            disabled={markingAllRead}
            className="self-start sm:self-auto flex items-center gap-1.5"
          >
            {markingAllRead ? (
              <LoadingSpinner size={14} />
            ) : (
              <CheckCircleIcon className="w-4 h-4 text-green-600" />
            )}
            {markingAllRead ? "Marking…" : "Mark all as read"}
          </Button>
        )}
      </div>

      {/* ------------------------------------------------------------------ */}
      {/* Top View Switcher Tabs (All Feed vs Reminders Center)             */}
      {/* ------------------------------------------------------------------ */}
      <div className="border-b border-gray-200 flex gap-6">
        <button
          onClick={() => setActiveView("feed")}
          className={`pb-3 text-sm font-semibold transition-colors relative flex items-center gap-2 ${
            activeView === "feed"
              ? "text-[#003087] border-b-2 border-[#003087]"
              : "text-gray-500 hover:text-gray-700"
          }`}
        >
          <BellIcon className="w-4 h-4" />
          All Notifications Feed
        </button>

        <button
          onClick={() => setActiveView("reminders")}
          className={`pb-3 text-sm font-semibold transition-colors relative flex items-center gap-2 ${
            activeView === "reminders"
              ? "text-[#003087] border-b-2 border-[#003087]"
              : "text-gray-500 hover:text-gray-700"
          }`}
        >
          <ClockIcon className="w-4 h-4" />
          Reminders Center
          <span className="text-xs bg-amber-100 text-amber-800 font-bold px-2 py-0.5 rounded-full">
            Dedicated
          </span>
        </button>
      </div>

      {/* ================================================================== */}
      {/* VIEW 1: All Notifications Feed                                     */}
      {/* ================================================================== */}
      {activeView === "feed" && (
        <div className="space-y-4">
          {/* Filter Bar */}
          <div className="bg-white rounded-xl border border-gray-200 p-4 flex flex-wrap items-center justify-between gap-3">
            {/* Type filter tabs */}
            <div className="flex items-center gap-1.5 flex-wrap">
              {TYPE_FILTER_TABS.map((tab) => (
                <button
                  key={tab.value}
                  onClick={() => {
                    setFeedTypeFilter(tab.value);
                    setFeedPage(0);
                  }}
                  className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                    feedTypeFilter === tab.value
                      ? "bg-[#003087] text-white"
                      : "bg-gray-50 border border-gray-200 text-gray-600 hover:bg-gray-100"
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            {/* Read / Unread toggle */}
            <label className="flex items-center gap-2 text-xs font-semibold text-gray-700 cursor-pointer select-none">
              <input
                type="checkbox"
                checked={feedUnreadOnly}
                onChange={(e) => {
                  setFeedUnreadOnly(e.target.checked);
                  setFeedPage(0);
                }}
                className="rounded border-gray-300 text-[#003087] focus:ring-[#003087]"
              />
              Show Unread Only
            </label>
          </div>

          {/* Feed List */}
          {feedLoading ? (
            <div className="flex justify-center py-16">
              <LoadingSpinner size={32} />
            </div>
          ) : notifications.length === 0 ? (
            <EmptyState
              title="No notifications"
              description={
                feedUnreadOnly
                  ? "You have no unread notifications."
                  : "No notifications found in this category."
              }
            />
          ) : (
            <div className="space-y-3">
              {notifications.map((item) => {
                const IconComp = typeIcon(item.type);
                return (
                  <div
                    key={item.id}
                    className={`bg-white rounded-xl border p-4 transition-all ${
                      !item.read
                        ? "border-blue-200 bg-blue-50/20 shadow-sm"
                        : "border-gray-200 hover:border-gray-300"
                    }`}
                  >
                    <div className="flex items-start gap-3.5">
                      {/* Icon */}
                      <div
                        className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${
                          !item.read ? "bg-[#003087]/10 text-[#003087]" : "bg-gray-100 text-gray-500"
                        }`}
                      >
                        <IconComp className="w-5 h-5" />
                      </div>

                      {/* Content */}
                      <div className="flex-1 min-w-0 space-y-1.5">
                        <div className="flex items-start justify-between gap-2">
                          <div className="flex items-center gap-2 flex-wrap">
                            {!item.read && (
                              <span className="w-2 h-2 rounded-full bg-[#003087] shrink-0" />
                            )}
                            <h3 className="text-sm font-semibold text-gray-900">{item.title}</h3>
                            <Badge tone={typeBadgeTone(item.type)}>{item.type}</Badge>

                            {item.notificationStatus && (
                              <Badge tone={statusBadgeTone(item.notificationStatus)}>
                                {item.notificationStatus}
                              </Badge>
                            )}
                          </div>

                          <div className="flex items-center gap-2 shrink-0">
                            {!item.read && (
                              <button
                                onClick={() => handleMarkRead(item.id)}
                                className="text-xs text-[#003087] font-semibold hover:underline whitespace-nowrap"
                              >
                                Mark Read
                              </button>
                            )}
                            <button
                              onClick={() => handleDismiss(item.id)}
                              className="p-1 rounded text-gray-400 hover:text-red-600 hover:bg-gray-100 transition-colors"
                              title="Dismiss"
                            >
                              <XIcon className="w-4 h-4" />
                            </button>
                          </div>
                        </div>

                        <p className="text-xs text-gray-600 leading-relaxed">{item.description}</p>

                        {/* Metadata row (Student, Fee, Due Date, Timestamp) */}
                        <div className="flex flex-wrap items-center gap-3 text-xs text-gray-500 pt-1 border-t border-gray-100">
                          <span>
                            {item.date} {item.time ? `• ${item.time}` : ""}
                          </span>

                          {item.studentName && (
                            <>
                              <span>•</span>
                              <span className="font-medium text-gray-800">
                                Student: {item.studentName}
                              </span>
                            </>
                          )}

                          {item.feeType && (
                            <>
                              <span>•</span>
                              <span>Fee: {item.feeType}</span>
                            </>
                          )}

                          {item.feeAmountEGP !== undefined && item.feeAmountEGP !== null && (
                            <>
                              <span>•</span>
                              <span className="font-semibold text-gray-900">
                                {formatEGP(item.feeAmountEGP)}
                              </span>
                            </>
                          )}

                          {item.dueDate && (
                            <>
                              <span>•</span>
                              <span className="text-amber-700 font-medium">
                                Due: {item.dueDate}{" "}
                                {item.daysUntilDue !== undefined && item.daysUntilDue !== null
                                  ? `(${item.daysUntilDue} days left)`
                                  : ""}
                              </span>
                            </>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })}

              {/* Feed Pagination */}
              {feedTotalPages > 1 && (
                <div className="flex justify-center pt-4">
                  <Pagination
                    page={feedPage + 1}
                    totalPages={feedTotalPages}
                    onPageChange={(p) => setFeedPage(p - 1)}
                  />
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* ================================================================== */}
      {/* VIEW 2: Dedicated Reminders Center                                  */}
      {/* ================================================================== */}
      {activeView === "reminders" && (
        <div className="space-y-4">
          {/* Reminders Status Filters */}
          <div className="bg-white rounded-xl border border-gray-200 p-4 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">
                Filter Status:
              </span>
              <div className="flex items-center gap-1.5 flex-wrap">
                {REMINDER_STATUS_TABS.map((tab) => (
                  <button
                    key={tab.value}
                    onClick={() => {
                      setReminderStatusFilter(tab.value);
                      setRemindersPage(0);
                    }}
                    className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
                      reminderStatusFilter === tab.value
                        ? "bg-[#003087] text-white"
                        : "bg-gray-50 border border-gray-200 text-gray-600 hover:bg-gray-100"
                    }`}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            </div>

            <p className="text-xs text-gray-500">
              Total Reminders: <span className="font-semibold text-gray-800">{remindersTotal}</span>
            </p>
          </div>

          {/* Reminders Table */}
          {remindersLoading ? (
            <div className="flex justify-center py-16">
              <LoadingSpinner size={32} />
            </div>
          ) : reminders.length === 0 ? (
            <EmptyState
              title="No reminders found"
              description="No automated parent payment reminders match your criteria."
            />
          ) : (
            <>
              <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
                <Table<SchoolNotificationItem>
                  columns={reminderColumns}
                  rows={reminders}
                  rowKey={(row) => row.id}
                />
              </div>

              {/* Reminders Pagination */}
              {remindersTotalPages > 1 && (
                <div className="flex justify-center pt-4">
                  <Pagination
                    page={remindersPage + 1}
                    totalPages={remindersTotalPages}
                    onPageChange={(p) => setRemindersPage(p - 1)}
                  />
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
