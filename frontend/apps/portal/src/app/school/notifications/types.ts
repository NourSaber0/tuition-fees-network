export interface SchoolNotificationItem {
  id: string;
  type: "payment" | "upload" | "reminder" | "penalty" | string;
  title: string;
  description: string;
  studentName?: string | null;
  feeType?: string | null;
  feeAmountEGP?: number | null;
  dueDate?: string | null;
  daysUntilDue?: number | null;
  /** "Scheduled" | "Sent" | "Failed" for reminders */
  notificationStatus?: string | null;
  date: string;
  time?: string | null;
  read: boolean;
  relatedId?: string | null;
}

export interface SchoolNotificationListResponse {
  data: SchoolNotificationItem[];
  unreadCount: number;
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface UnreadCountResponse {
  count: number;
}

