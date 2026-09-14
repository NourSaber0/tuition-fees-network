"use client";

import { useEffect, useState } from "react";
import { useApiClient } from "@tuition/api-client";
import { Table, type Column, Badge, type BadgeTone, Modal, LoadingSpinner } from "@tuition/ui";
import type { FeeSubmissionSummaryDto, FeeSubmissionDetailDto } from "../types";

const STATUS_TONE: Record<string, BadgeTone> = {
  PROCESSED: "success",
  PARTIAL: "warning",
  REJECTED: "danger",
};

export function FeeSubmissionsTab({ institutionId }: { institutionId: string }) {
  const apiClient = useApiClient();
  const [submissions, setSubmissions] = useState<FeeSubmissionSummaryDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [openId, setOpenId] = useState<string | null>(null);
  const [detail, setDetail] = useState<FeeSubmissionDetailDto | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);

  useEffect(() => {
    apiClient
      .get<FeeSubmissionSummaryDto[]>(`/institutions/${institutionId}/fee-submissions`)
      .then(setSubmissions)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load fee submissions"));
  }, [apiClient, institutionId]);

  function openDetail(submissionId: string) {
    setOpenId(submissionId);
    setDetail(null);
    setDetailError(null);
    apiClient
      .get<FeeSubmissionDetailDto>(`/institutions/${institutionId}/fee-submissions/${submissionId}`)
      .then(setDetail)
      .catch((err) => setDetailError(err instanceof Error ? err.message : "Failed to load submission detail"));
  }

  if (error) return <p className="text-sm text-red-600">{error}</p>;

  const columns: Column<FeeSubmissionSummaryDto>[] = [
    { key: "fileName", header: "File" },
    { key: "totalRows", header: "Rows" },
    { key: "successfulRows", header: "Successful", render: (r) => <span className="text-green-700">{r.successfulRows}</span> },
    { key: "failedRows", header: "Failed", render: (r) => (r.failedRows > 0 ? <span className="text-red-600">{r.failedRows}</span> : "0") },
    { key: "status", header: "Status", render: (r) => <Badge tone={STATUS_TONE[r.status] ?? "neutral"}>{r.status}</Badge> },
    { key: "uploadedAt", header: "Uploaded" },
  ];

  return (
    <>
      <Table
        columns={columns}
        rows={submissions ?? []}
        rowKey={(r) => r.submissionId}
        loading={submissions === null}
        emptyTitle="No fee submissions yet"
        emptyDescription="Fee rosters uploaded by this institution's admin will appear here."
        onRowClick={(r) => openDetail(r.submissionId)}
      />

      <Modal open={openId !== null} onClose={() => setOpenId(null)} title="Submission Detail">
        {detailError && <p className="text-sm text-red-600">{detailError}</p>}
        {!detail && !detailError && (
          <div className="flex justify-center py-8">
            <LoadingSpinner />
          </div>
        )}
        {detail && (
          <div className="space-y-3">
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <span className="text-[var(--cib-text-muted)]">File:</span> {detail.fileName}
              </div>
              <div>
                <span className="text-[var(--cib-text-muted)]">Uploaded:</span> {detail.uploadedAt}
              </div>
              <div>
                <span className="text-[var(--cib-text-muted)]">Rows:</span> {detail.totalRows} total, {detail.successfulRows}{" "}
                successful, {detail.failedRows} failed
              </div>
              <div>
                <Badge tone={STATUS_TONE[detail.status] ?? "neutral"}>{detail.status}</Badge>
              </div>
            </div>

            {detail.errors.length > 0 ? (
              <div className="max-h-64 overflow-y-auto border border-[var(--cib-border)] rounded-md">
                <table className="w-full text-xs">
                  <thead className="bg-[#F8FAFD] sticky top-0">
                    <tr>
                      <th className="text-left px-3 py-2 font-semibold text-gray-400">Row</th>
                      <th className="text-left px-3 py-2 font-semibold text-gray-400">Error</th>
                      <th className="text-left px-3 py-2 font-semibold text-gray-400">Raw Data</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.errors.map((e, i) => (
                      <tr key={i} className="border-t border-gray-50">
                        <td className="px-3 py-2 font-mono">{e.rowNumber}</td>
                        <td className="px-3 py-2 text-red-600">{e.errorMessage}</td>
                        <td className="px-3 py-2 font-mono text-gray-500 truncate max-w-xs">{e.rawRow}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="text-xs text-green-700">All rows processed successfully.</p>
            )}
          </div>
        )}
      </Modal>
    </>
  );
}
