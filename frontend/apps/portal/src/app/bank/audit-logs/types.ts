export interface AuditLogDto {
  id: string;
  user: string;
  actorId: string | null;
  role: string;
  actorType: string;
  action: string;
  entity: string;
  entityId: string;
  targetResource: string | null;
  prevValue: string | null;
  newValue: string | null;
  timestamp: string;
  ipAddress: string;
  severity: "info" | "warning" | "critical" | string;
}

export interface AuditLogStatsDto {
  total: number;
  critical: number;
  warning: number;
  info: number;
  bySeverity: Record<string, number>;
}
