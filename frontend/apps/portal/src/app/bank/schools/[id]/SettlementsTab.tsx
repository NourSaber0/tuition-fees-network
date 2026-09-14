"use client";

import { useEffect, useState } from "react";
import { useApiClient } from "@tuition/api-client";
import { Table, type Column, Badge, type BadgeTone } from "@tuition/ui";
import type { InstitutionSettlementDto, InstitutionSettlementsResponse } from "../types";

const STATUS_TONE: Record<string, BadgeTone> = {
  Completed: "success",
  Processing: "info",
  Pending: "warning",
  Failed: "danger",
};

function money(n: number): string {
  return Math.round(n).toLocaleString();
}

export function SettlementsTab({ institutionId }: { institutionId: string }) {
  const apiClient = useApiClient();
  const [result, setResult] = useState<InstitutionSettlementsResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiClient
      .get<InstitutionSettlementsResponse>(`/institutions/${institutionId}/settlements`)
      .then(setResult)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load settlements"));
  }, [apiClient, institutionId]);

  if (error) return <p className="text-sm text-red-600">{error}</p>;

  const columns: Column<InstitutionSettlementDto>[] = [
    { key: "id", header: "Settlement ID", render: (r) => <span className="font-mono text-xs">{r.id}</span> },
    { key: "date", header: "Date" },
    { key: "grossEGP", header: "Gross", render: (r) => `EGP ${money(r.grossEGP)}` },
    { key: "cibFeeEGP", header: "CIB Fee (2%)", render: (r) => `EGP ${money(r.cibFeeEGP)}` },
    { key: "netEGP", header: "Net Settled", render: (r) => <span className="font-semibold">EGP {money(r.netEGP)}</span> },
    { key: "status", header: "Status", render: (r) => <Badge tone={STATUS_TONE[r.status] ?? "neutral"}>{r.status}</Badge> },
    { key: "txRef", header: "Transaction Ref", render: (r) => <span className="font-mono text-xs">{r.txRef}</span> },
  ];

  return (
    <div className="space-y-4">
      {result && (
        <div className="grid grid-cols-3 gap-3">
          <SummaryCard label="Total Settled" value={`EGP ${money(result.summary.totalSettledEGP)}`} />
          <SummaryCard label="Settlement Records" value={String(result.summary.recordCount)} />
          <SummaryCard label="Last Settlement" value={result.summary.lastSettlementDate} />
        </div>
      )}
      <Table
        columns={columns}
        rows={result?.data ?? []}
        rowKey={(r) => r.id}
        loading={result === null}
        emptyTitle="No settlements recorded yet"
      />
    </div>
  );
}

function SummaryCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-[#F8FAFD] rounded-lg px-4 py-3">
      <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">{label}</p>
      <p className="text-base font-bold mt-0.5" style={{ color: "var(--cib-text)" }}>
        {value}
      </p>
    </div>
  );
}
