"use client";

import { useEffect, useState } from "react";
import { useApiClient } from "@tuition/api-client";
import { Table, type Column, Badge, type BadgeTone } from "@tuition/ui";
import type { InstitutionStudentDto } from "../types";

const STATUS_TONE: Record<string, BadgeTone> = {
  Paid: "success",
  Partial: "warning",
  Unpaid: "danger",
};

function money(n: number): string {
  return Math.round(n).toLocaleString();
}

export function StudentsTab({ institutionId }: { institutionId: string }) {
  const apiClient = useApiClient();
  const [students, setStudents] = useState<InstitutionStudentDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiClient
      .get<InstitutionStudentDto[]>(`/institutions/${institutionId}/students`)
      .then(setStudents)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load students"));
  }, [apiClient, institutionId]);

  if (error) {
    return <p className="text-sm text-red-600">{error}</p>;
  }

  const columns: Column<InstitutionStudentDto>[] = [
    { key: "fullName", header: "Student", render: (r) => <span className="font-medium">{r.fullName}</span> },
    { key: "dateOfBirth", header: "Date of Birth" },
    { key: "totalFeesEGP", header: "Total Fees", render: (r) => `EGP ${money(r.totalFeesEGP)}` },
    { key: "paidEGP", header: "Paid", render: (r) => `EGP ${money(r.paidEGP)}` },
    { key: "outstandingEGP", header: "Outstanding", render: (r) => `EGP ${money(r.outstandingEGP)}` },
    { key: "status", header: "Status", render: (r) => <Badge tone={STATUS_TONE[r.status] ?? "neutral"}>{r.status}</Badge> },
  ];

  return (
    <Table
      columns={columns}
      rows={students ?? []}
      rowKey={(r) => r.studentId}
      loading={students === null}
      emptyTitle="No students enrolled yet"
    />
  );
}
