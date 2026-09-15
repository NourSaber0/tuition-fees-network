export interface NotificationAction {
  label: string;
  screen: string | null;
  entityId: string | null;
}

export interface BackOfficeNotificationDto {
  id: string;
  type: string;
  severity: string;
  title: string;
  body: string;
  meta: string | null;
  read: boolean;
  createdAt: string;
  action: NotificationAction | null;
}

export interface NotificationListResponse {
  data: BackOfficeNotificationDto[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  unreadCount: number;
}
