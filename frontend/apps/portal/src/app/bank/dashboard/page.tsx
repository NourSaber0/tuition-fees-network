"use client";

import { useAuth } from "@tuition/api-client";
import { Badge } from "@tuition/ui";

export default function BankDashboardPage() {
  const { user } = useAuth();

  return (
    <div className="flex flex-col gap-3">
      <Badge tone="info">AUTH ticket complete - real dashboard content is BO-P2</Badge>
      <h1 className="text-lg font-semibold" style={{ color: "var(--cib-text)" }}>
        Welcome, {user?.name}
      </h1>
      <p className="text-sm" style={{ color: "var(--cib-text-muted)" }}>
        Role: {user?.role} - permissions: {user?.permissions.join(", ")}
      </p>
    </div>
  );
}
