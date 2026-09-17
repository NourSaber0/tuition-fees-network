"use client";

import { useEffect } from "react";

import { useRouter } from "next/navigation";

import { useAuth, isSchoolRole } from "@tuition/api-client";

import {
  PortalShell,
  LoadingSpinner,
  DashboardIcon,
  UserIcon,
  CreditCardIcon,
  DownloadIcon,
  TransactionIcon,
  ReconcileIcon,
  ReportIcon,
  BellIcon,
  UsersIcon,
  SettingsIcon,
  type PortalNavItem,
} from "@tuition/ui";

import CIBAssistant from "../components/CIBAssistant";
const NAV_ITEMS: PortalNavItem[] = [
  { id: "dashboard", label: "Dashboard", href: "/school/dashboard", Icon: DashboardIcon },
  { id: "students", label: "Students", href: "/school/students", Icon: UserIcon },
  { id: "fee-management", label: "Fee Management", href: "/school/fee-management", Icon: CreditCardIcon },
  { id: "fee-upload", label: "Fee Upload", href: "/school/fee-upload", Icon: DownloadIcon },
  { id: "payments", label: "Payments", href: "/school/payments", Icon: TransactionIcon },
  { id: "reconciliation", label: "Reconciliation", href: "/school/reconciliation", Icon: ReconcileIcon },
  { id: "reports", label: "Reports", href: "/school/reports", Icon: ReportIcon },
  { id: "notifications", label: "Notifications", href: "/school/notifications", Icon: BellIcon },
  { id: "users", label: "School Users", href: "/school/users", Icon: UsersIcon },
  { id: "settings", label: "Settings", href: "/school/settings", Icon: SettingsIcon },
];

const ROLE_LABELS: Record<string, string> = {
  "school-admin": "School Admin",
  "school-finance": "School Finance",
};

export default function SchoolLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const router = useRouter();
  const { status, user, logout } = useAuth();

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
    } else if (
      status === "authenticated" &&
      user &&
      !isSchoolRole(user.role)
    ) {
      // Defense in depth only - the backend's @PreAuthorize is what actually
      // blocks a bank account from calling school endpoints.
      router.replace("/login");
    }
  }, [status, user, router]);

  if (
    status !== "authenticated" ||
    !user ||
    !isSchoolRole(user.role)
  ) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <LoadingSpinner />
      </div>
    );
  }

  const navItems = NAV_ITEMS.filter((item) =>
    user.permissions.includes(item.id)
  );

  const assistantRole =
    user.role === "school-admin"
      ? "School Admin"
      : "School Finance";

  return (
    <PortalShell
      brandLabel={user.schoolName ?? "School Portal"}
      navItems={navItems}
      user={{
        name: user.name,
        initials: user.initials ?? "??",
        roleLabel: ROLE_LABELS[user.role] ?? user.role,
      }}
      onLogout={() => {
        logout();
        router.replace("/login");
      }}
    >
      {children}

      <CIBAssistant
        currentUserRole={assistantRole}
        navigate={(page) => {
          router.push(`/school/${page}`);
        }}
      />
    </PortalShell>
  );
}