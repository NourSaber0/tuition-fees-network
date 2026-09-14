"use client";

import { useAuth } from "@tuition/api-client";
import { Badge, Button, LoadingSpinner, BankIcon } from "@tuition/ui";

export default function Home() {
  const { status, user, logout } = useAuth();

  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-4 bg-[var(--cib-bg)] p-8">
      <BankIcon className="w-10 h-10 text-[var(--cib-blue)]" />
      <h1 className="text-lg font-semibold text-[var(--cib-text)]">CIB Tuition Network Portal</h1>

      {status === "loading" && <LoadingSpinner />}

      {status === "unauthenticated" && (
        <Badge tone="neutral">Not signed in - the shared /login page lands in the AUTH ticket</Badge>
      )}

      {status === "authenticated" && user && (
        <div className="flex flex-col items-center gap-3">
          <Badge tone="success">
            Signed in as {user.name} ({user.role})
          </Badge>
          <Button variant="secondary" size="sm" onClick={() => logout()}>
            Sign out
          </Button>
        </div>
      )}
    </div>
  );
}
