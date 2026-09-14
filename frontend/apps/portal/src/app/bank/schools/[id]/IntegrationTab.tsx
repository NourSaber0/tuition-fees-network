"use client";

import { useEffect, useState } from "react";
import { useApiClient } from "@tuition/api-client";
import { Badge, type BadgeTone, LoadingSpinner } from "@tuition/ui";
import type { InstitutionIntegrationDto, IntegrationStatus } from "../types";

const TONE: Record<IntegrationStatus, BadgeTone> = {
  NOT_INTEGRATED: "neutral",
  PENDING: "warning",
  INTEGRATED: "success",
  FAILED: "danger",
};

export function IntegrationTab({ institutionId }: { institutionId: string }) {
  const apiClient = useApiClient();
  const [data, setData] = useState<InstitutionIntegrationDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiClient
      .get<InstitutionIntegrationDto>(`/institutions/${institutionId}/integration`)
      .then(setData)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load integration status"));
  }, [apiClient, institutionId]);

  if (error) return <p className="text-sm text-red-600">{error}</p>;
  if (!data) {
    return (
      <div className="flex justify-center py-8">
        <LoadingSpinner />
      </div>
    );
  }

  return (
    <div className="max-w-lg">
      <div className="flex items-center gap-2 mb-3">
        <Badge tone={TONE[data.status]}>{data.status.replace("_", " ")}</Badge>
        {data.configured && <Badge tone="info">Configured</Badge>}
      </div>
      <p className="text-sm text-[var(--cib-text)]">{data.message}</p>
    </div>
  );
}
