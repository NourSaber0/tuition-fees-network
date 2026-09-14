"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth, isBankRole, isSchoolRole } from "@tuition/api-client";
import { LoadingSpinner } from "@tuition/ui";

export default function Home() {
  const router = useRouter();
  const { status, user } = useAuth();

  useEffect(() => {
    if (status === "unauthenticated") {
      router.replace("/login");
    } else if (status === "authenticated" && user) {
      if (isBankRole(user.role)) router.replace("/bank/dashboard");
      else if (isSchoolRole(user.role)) router.replace("/school/dashboard");
      else router.replace("/login");
    }
  }, [status, user, router]);

  return (
    <div className="flex flex-1 items-center justify-center" style={{ background: "var(--cib-bg)" }}>
      <LoadingSpinner />
    </div>
  );
}
