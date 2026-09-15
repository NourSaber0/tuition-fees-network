"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useApiClient } from "@tuition/api-client";
import {
  LoadingSpinner,
  Pagination,
  BellIcon,
  AlertIcon,
  SchoolIcon,
  TransactionIcon,
  ReconcileIcon,
  CheckCircleIcon,
  XIcon,
} from "@tuition/ui";
import type { BackOfficeNotificationDto, NotificationListResponse } from "./types";
import { typeLabel, typeIconStyle, typeLabelClass, severityClass, formatTimestamp } from "./badges";

const TYPE_ICON: Record<string, typeof TransactionIcon> = {
  FAILED_PAYMENT: TransactionIcon,
  RECON_EXCEPTION: ReconcileIcon,
  INSTITUTION_ISSUE: SchoolIcon,
  NEW_INSTITUTION: SchoolIcon,
  SYSTEM_ALERT: AlertIcon,
};

const FILTER_TABS: { label: string; type: string | null }[] = [
  { label: "All", type: null },
  { label: "Failed Payments", type: "FAILED_PAYMENT" },
  { label: "Reconciliation", type: "RECON_EXCEPTION" },
  { label: "Institution Issues", type: "INSTITUTION_ISSUE" },
  { label: "New Institutions", type: "NEW_INSTITUTION" },
  { label: "System Alerts", type: "SYSTEM_ALERT" },
];

export default function NotificationsPage() {
  const apiClient = useApiClient();
  const router = useRouter();

  const [notifications, setNotifications] = useState<BackOfficeNotificationDto[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [filterType, setFilterType] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [markingAllRead, setMarkingAllRead] = useState(false);

  const loadAll = useCallback(() => {
    const params = new URLSearchParams();
    if (filterType) params.set("type", filterType);
    params.set("page", String(page));
    params.set("pageSize", "10");

    apiClient
      .get<NotificationListResponse>(`/notifications?${params.toString()}`)
      .then((res) => {
        setNotifications(res.data ?? []);
        setUnreadCount(res.unreadCount ?? 0);
        setTotal(res.total ?? 0);
        setTotalPages(Math.max(1, res.totalPages ?? 1));
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [apiClient, filterType, page]);

  useEffect(() => {
    loadAll();
  }, [loadAll, refreshTrigger]);

  const handleMarkRead = async (id: string) => {
    try {
      await apiClient.post(`/notifications/${id}/read`, {});
      setRefreshTrigger((p) => p + 1);
    } catch {
      // ignore - list will still reflect the current server state on next refresh
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

  const handleAction = (notif: BackOfficeNotificationDto) => {
    if (!notif.read) handleMarkRead(notif.id);
    if (notif.action?.screen) router.push(`/bank/${notif.action.screen}`);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <p className="text-sm text-gray-400">
            {total} notification{total !== 1 ? "s" : ""}
          </p>
          {unreadCount > 0 && (
            <span className="bg-red-100 text-red-700 text-[11px] font-bold px-2 py-0.5 rounded-full">
              {unreadCount} unread
            </span>
          )}
        </div>
        {unreadCount > 0 && (
          <button
            onClick={handleMarkAllRead}
            disabled={markingAllRead}
            className="text-xs text-[#003087] font-semibold hover:underline flex items-center gap-1 disabled:opacity-50"
          >
            <CheckCircleIcon className="w-3.5 h-3.5" /> {markingAllRead ? "Marking…" : "Mark all as read"}
          </button>
        )}
      </div>

      <div className="flex items-center gap-1 flex-wrap">
        {FILTER_TABS.map((tab) => (
          <button
            key={tab.label}
            onClick={() => {
              setFilterType(tab.type);
              setPage(0);
            }}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              filterType === tab.type ? "bg-[#003087] text-white" : "bg-white border border-[#DDE3EF] text-gray-500 hover:bg-gray-50"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="flex flex-col items-center justify-center py-24 space-y-3">
          <LoadingSpinner />
          <p className="text-sm text-gray-500">Loading notifications...</p>
        </div>
      ) : (
        <>
          <div className="space-y-2">
            {notifications.length === 0 ? (
              <div className="bg-white rounded-xl border border-[#E8EDF5] p-12 text-center">
                <BellIcon className="w-8 h-8 text-gray-200 mx-auto mb-3" />
                <p className="text-sm font-semibold text-gray-400">No notifications in this category</p>
                <p className="text-xs text-gray-300 mt-1">You&apos;re all caught up.</p>
              </div>
            ) : (
              notifications.map((notif) => {
                const Icon = TYPE_ICON[notif.type] ?? AlertIcon;
                const iconStyle = typeIconStyle(notif.type);
                return (
                  <div
                    key={notif.id}
                    className={`bg-white rounded-xl border p-4 transition-all ${
                      !notif.read ? "border-[#003087]/20 shadow-sm" : "border-[#E8EDF5]"
                    }`}
                  >
                    <div className="flex items-start gap-3">
                      <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${iconStyle.bg}`}>
                        <Icon className={`w-4 h-4 ${iconStyle.iconColor}`} />
                      </div>

                      <div className="flex-1 min-w-0">
                        <div className="flex items-start justify-between gap-2">
                          <div className="flex items-center gap-2 flex-wrap">
                            {!notif.read && <span className="w-2 h-2 bg-[#003087] rounded-full shrink-0" />}
                            <span className="text-sm font-semibold text-gray-800">{notif.title}</span>
                            <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded border ${severityClass(notif.severity)}`}>
                              {notif.severity.toLowerCase()}
                            </span>
                          </div>
                          <div className="flex items-center gap-1 shrink-0">
                            {!notif.read && (
                              <button
                                onClick={() => handleMarkRead(notif.id)}
                                className="text-[11px] text-[#003087] font-semibold hover:underline whitespace-nowrap"
                              >
                                Mark read
                              </button>
                            )}
                            <button
                              onClick={() => handleDismiss(notif.id)}
                              className="p-1 rounded hover:bg-gray-100 text-gray-300 hover:text-gray-500 transition-colors"
                            >
                              <XIcon className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </div>

                        <p className="text-xs text-gray-500 mt-1 leading-relaxed">{notif.body}</p>

                        {notif.meta && <p className="text-[11px] font-semibold text-gray-400 mt-1.5 font-mono">{notif.meta}</p>}

                        <div className="flex items-center justify-between gap-2 mt-2.5">
                          <div className="flex items-center gap-2">
                            <span className="text-[11px] text-gray-400">{formatTimestamp(notif.createdAt)}</span>
                            <span className="text-gray-200">·</span>
                            <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded ${typeLabelClass(notif.type)}`}>
                              {typeLabel(notif.type)}
                            </span>
                          </div>

                          {notif.action && (
                            <button
                              onClick={() => handleAction(notif)}
                              className="text-[11px] font-semibold text-[#003087] border border-[#003087]/25 rounded-lg px-2.5 py-1 hover:bg-[#EBF1FB] transition-colors whitespace-nowrap shrink-0"
                            >
                              {notif.action.label} →
                            </button>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              })
            )}
          </div>

          {notifications.length > 0 && (
            <Pagination page={page + 1} totalPages={totalPages} onPageChange={(p) => setPage(p - 1)} />
          )}
        </>
      )}
    </div>
  );
}
