"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth, isBankRole } from "@tuition/api-client";
import {
  PortalShell,
  LoadingSpinner,
  DashboardIcon,
  SchoolIcon,
  TransactionIcon,
  ReconcileIcon,
  EPPIcon,
  ReportIcon,
  BellIcon,
  AuditIcon,
  UsersIcon,
  SettingsIcon,
  type PortalNavItem,
} from "@tuition/ui";

const NAV_ITEMS: PortalNavItem[] = [
  { id: "dashboard", label: "Dashboard", href: "/bank/dashboard", Icon: DashboardIcon },
  { id: "schools", label: "Institution Management", href: "/bank/schools", Icon: SchoolIcon },
  { id: "transactions", label: "Transactions", href: "/bank/transactions", Icon: TransactionIcon },
  { id: "reconciliation", label: "Reconciliation", href: "/bank/reconciliation", Icon: ReconcileIcon },
  { id: "epp", label: "EPP Plans", href: "/bank/epp", Icon: EPPIcon },
  { id: "reports", label: "Reports", href: "/bank/reports", Icon: ReportIcon },
  { id: "notifications", label: "Notifications", href: "/bank/notifications", Icon: BellIcon },
  { id: "audit-logs", label: "Audit Logs", href: "/bank/audit-logs", Icon: AuditIcon },
  { id: "users", label: "Users & Roles", href: "/bank/users", Icon: UsersIcon },
  { id: "settings", label: "System Settings", href: "/bank/settings", Icon: SettingsIcon },
];

const ROLE_LABELS: Record<string, string> = {
  "bank-admin": "Bank Admin",
  "bank-operations": "Operations",
  "bank-finance": "Finance",
  "bank-reconciliation": "Reconciliation",
};

export default function BankLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { status, user, logout } = useAuth();

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
    } else if (status === "authenticated" && user && !isBankRole(user.role)) {
      // Defense in depth only - the backend's @PreAuthorize is what actually
      // blocks a school account from calling bank endpoints.
      router.replace("/login");
    }
  }, [status, user, router]);

  if (status !== "authenticated" || !user || !isBankRole(user.role)) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <LoadingSpinner />
      </div>
    );
  }

  const navItems = NAV_ITEMS.filter((item) => user.permissions.includes(item.id));

  return (
    <PortalShell
      brandLabel="Back Office"
      navItems={navItems}
      user={{ name: user.name, initials: user.initials ?? "??", roleLabel: ROLE_LABELS[user.role] ?? user.role }}
      onLogout={() => {
        logout();
        router.replace("/login");
      }}
    >
      {children}
    </PortalShell>
  );
}
