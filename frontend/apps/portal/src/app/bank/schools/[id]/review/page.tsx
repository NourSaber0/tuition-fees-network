"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { useApiClient } from "@tuition/api-client";
import { LoadingSpinner, EmptyState, ChevronLeftIcon, CheckIcon, XIcon } from "@tuition/ui";
import type { InstitutionApplicationDto } from "../../types";
import { REG_STATUS_LABEL, REG_STATUS_STYLE, INSTITUTION_TYPE_LABEL, formatDate } from "../../badges";

const REJECT_REASONS = ["Incomplete documentation", "Invalid commercial registry", "License not recognized", "Duplicate registration", "Compliance issue", "Other"];

export default function InstitutionReviewPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const apiClient = useApiClient();

  const [app, setApp] = useState<InstitutionApplicationDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [approveModal, setApproveModal] = useState(false);
  const [rejectModal, setRejectModal] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [rejectNotes, setRejectNotes] = useState("");
  const [done, setDone] = useState<"approved" | "rejected" | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    apiClient
      .get<InstitutionApplicationDto>(`/institutions/${id}/application`)
      .then(setApp)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load application"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function confirmApprove() {
    setLoading(true);
    try {
      await apiClient.post(`/institutions/${id}/approve`);
      setApproveModal(false);
      setDone("approved");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Approval failed");
      setApproveModal(false);
    } finally {
      setLoading(false);
    }
  }

  async function confirmReject() {
    setLoading(true);
    try {
      await apiClient.post(`/institutions/${id}/reject`, { reason: rejectReason, notes: rejectNotes || undefined });
      setRejectModal(false);
      setDone("rejected");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Rejection failed");
      setRejectModal(false);
    } finally {
      setLoading(false);
    }
  }

  if (error && !app) return <EmptyState title="Could not load application" description={error} />;
  if (!app) {
    return (
      <div className="flex justify-center py-20">
        <LoadingSpinner />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <button onClick={() => router.push("/bank/schools")} className="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-700 transition-colors">
        <ChevronLeftIcon className="w-4 h-4" /> Back to Institutions
      </button>

      {error && <div className="rounded-lg px-4 py-3 text-sm bg-red-50 border border-red-200 text-red-700">{error}</div>}

      {done && (
        <div className={`flex items-center gap-2 rounded-xl px-5 py-3.5 text-sm font-semibold border ${done === "approved" ? "bg-green-50 border-green-200 text-green-800" : "bg-red-50 border-red-200 text-red-800"}`}>
          {done === "approved" ? <CheckIcon className="w-4 h-4" /> : <XIcon className="w-4 h-4" />}
          Application {done === "approved" ? "approved successfully. The institution account is now Active." : "rejected successfully."}
        </div>
      )}

      <div className="bg-white rounded-xl border p-6" style={{ borderColor: "#E8EDF5" }}>
        <div className="flex items-start justify-between mb-5 pb-5 border-b border-gray-100">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider">Institution Application</div>
              <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-[var(--cib-blue-light)] text-[var(--cib-blue)]">
                {INSTITUTION_TYPE_LABEL[app.institutionType]}
              </span>
            </div>
            <h2 className="text-xl font-bold" style={{ color: "var(--cib-text)" }}>
              {app.name}
            </h2>
            <p className="text-sm text-gray-400 mt-0.5">
              {app.registrationNumber} - Submitted {formatDate(app.registeredAt)}
            </p>
          </div>
          <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${REG_STATUS_STYLE[app.registrationStatus]}`}>
            {REG_STATUS_LABEL[app.registrationStatus]}
          </span>
        </div>

        <div className="grid grid-cols-2 gap-6">
          {(
            [
              ["Registration No.", app.registrationNumber],
              ["Institution Type", INSTITUTION_TYPE_LABEL[app.institutionType]],
              [app.institutionType === "UNIVERSITY" ? "Vice Chancellor / President" : "Principal", app.principalName],
              ["City", app.city],
              ["Sub-Type", app.subType],
              ["Phone", app.phone],
              ["Email", app.email],
              ["Students / Enrolled", app.studentCount.toLocaleString()],
            ] as [string, string][]
          ).map(([k, v]) => (
            <div key={k}>
              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">{k}</div>
              <div className="text-sm text-gray-800">{v}</div>
            </div>
          ))}
        </div>

        <div className="mt-6 pt-5 border-t border-gray-100">
          <h4 className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-3">
            Required Documents{!app.documentsTracked && <span className="ml-2 font-normal text-gray-400">(checklist only - not yet verified against uploads)</span>}
          </h4>
          <div className="grid grid-cols-3 gap-3">
            {app.requiredDocuments.map((doc) => (
              <div key={doc} className="flex items-center gap-2 text-xs text-gray-600">
                <span className="w-3.5 h-3.5 rounded-full border border-gray-300 shrink-0" />
                {doc}
              </div>
            ))}
          </div>
        </div>

        {!done && (app.registrationStatus === "PENDING" || app.registrationStatus === "UNDER_REVIEW") && (
          <div className="mt-6 pt-5 border-t border-gray-100 flex items-center justify-between">
            <div className="text-xs text-gray-400">
              Pending since: <strong className="text-gray-600">{formatDate(app.registeredAt)}</strong>
            </div>
            <div className="flex gap-3">
              <button onClick={() => setRejectModal(true)} className="px-4 py-2 text-sm font-semibold text-red-600 border border-red-200 rounded-lg hover:bg-red-50 transition-colors">
                Reject Application
              </button>
              <button onClick={() => setApproveModal(true)} className="px-4 py-2 text-sm font-semibold text-white bg-[var(--cib-blue)] rounded-lg hover:opacity-90 transition-colors flex items-center gap-2">
                <CheckIcon className="w-4 h-4" /> Approve Application
              </button>
            </div>
          </div>
        )}
      </div>

      {approveModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-sm mx-4">
            <div className="w-12 h-12 rounded-full bg-green-50 flex items-center justify-center mx-auto mb-4">
              <CheckIcon className="w-6 h-6 text-green-600" />
            </div>
            <h3 className="text-base font-semibold text-center mb-1" style={{ color: "var(--cib-text)" }}>
              Confirm Approval
            </h3>
            <p className="text-sm text-gray-500 text-center mb-1">
              You are about to approve <strong>{app.name}</strong>.
            </p>
            <p className="text-xs text-gray-400 text-center mb-5">This activates the institution account and enables fee collection.</p>
            <div className="flex gap-3">
              <button onClick={() => setApproveModal(false)} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
                Cancel
              </button>
              <button onClick={confirmApprove} disabled={loading} className="flex-1 py-2.5 text-sm font-semibold text-white bg-[var(--cib-blue)] rounded-lg hover:opacity-90 transition-colors disabled:opacity-50">
                Confirm Approval
              </button>
            </div>
          </div>
        </div>
      )}

      {rejectModal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-2xl shadow-2xl p-6 w-full max-w-md mx-4">
            <h3 className="text-base font-semibold mb-1" style={{ color: "var(--cib-text)" }}>
              Reject Application
            </h3>
            <p className="text-sm text-gray-400 mb-4">
              Provide a reason for rejecting <strong>{app.name}</strong>.
            </p>
            <select
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              className="w-full border border-[var(--cib-border)] rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[var(--cib-blue)] mb-3"
            >
              <option value="">Select reason...</option>
              {REJECT_REASONS.map((r) => (
                <option key={r}>{r}</option>
              ))}
            </select>
            <textarea
              placeholder="Additional notes (optional)..."
              value={rejectNotes}
              onChange={(e) => setRejectNotes(e.target.value)}
              className="w-full border border-[var(--cib-border)] rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[var(--cib-blue)] h-24 resize-none mb-4"
            />
            <div className="flex gap-3">
              <button onClick={() => setRejectModal(false)} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
                Cancel
              </button>
              <button
                onClick={confirmReject}
                disabled={!rejectReason || loading}
                className="flex-1 py-2.5 text-sm font-semibold text-white bg-red-600 rounded-lg hover:bg-red-700 transition-colors disabled:opacity-40"
              >
                Confirm Rejection
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
