"use client";

import { useEffect, useState } from "react";
import { useApiClient } from "@tuition/api-client";
import { LoadingSpinner, ExclamationIcon } from "@tuition/ui";
import type { InstitutionApplicationDto } from "../types";

export function ApplicationTab({ institutionId }: { institutionId: string }) {
  const apiClient = useApiClient();
  const [app, setApp] = useState<InstitutionApplicationDto | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiClient
      .get<InstitutionApplicationDto>(`/institutions/${institutionId}/application`)
      .then(setApp)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load application"));
  }, [apiClient, institutionId]);

  if (error) return <p className="text-sm text-red-600">{error}</p>;
  if (!app) {
    return (
      <div className="flex justify-center py-8">
        <LoadingSpinner />
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-3 gap-4">
        <Info label="Registration Number" value={app.registrationNumber} />
        <Info label="Institution Type" value={app.institutionType === "UNIVERSITY" ? "University" : "School"} />
        <Info label="Sub-Type" value={app.subType} />
        <Info label="City" value={app.city} />
        <Info label="Principal" value={app.principalName} />
        <Info label="Phone" value={app.phone} />
        <Info label="Email" value={app.email} />
        <Info label="Student Count" value={app.studentCount.toLocaleString()} />
        <Info label="Submitted" value={app.registeredAt} />
      </div>

      <div>
        <h3 className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-2">Required Documents</h3>
        {!app.documentsTracked && (
          <div className="flex items-start gap-2 text-xs text-amber-700 bg-amber-50 rounded-md px-3 py-2 mb-3">
            <ExclamationIcon className="w-4 h-4 shrink-0 mt-0.5" />
            <span>Document verification is not yet tracked in the system - this is the required checklist for this institution type only.</span>
          </div>
        )}
        <ul className="space-y-1.5">
          {app.requiredDocuments.map((doc) => (
            <li key={doc} className="flex items-center gap-2 text-sm text-[var(--cib-text)]">
              <span className="w-1.5 h-1.5 rounded-full bg-gray-300 shrink-0" />
              {doc}
            </li>
          ))}
        </ul>
      </div>

      {app.rejectionReason && (
        <div className="text-xs text-red-600 bg-red-50 rounded-md px-3 py-2">Rejection reason: {app.rejectionReason}</div>
      )}
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">{label}</dt>
      <dd className="text-sm text-[var(--cib-text)] mt-0.5">{value}</dd>
    </div>
  );
}
