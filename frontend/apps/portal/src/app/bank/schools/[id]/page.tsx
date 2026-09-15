"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { useApiClient } from "@tuition/api-client";
import { LoadingSpinner, EmptyState, Modal, useToast, ChevronLeftIcon, SchoolIcon, ReportIcon, ReconcileIcon, AlertIcon } from "@tuition/ui";
import type {
  InstitutionDetailDto,
  InstitutionStudentDto,
  InstitutionIntegrationDto,
  InstitutionSettlementsResponse,
  FeeSubmissionSummaryDto,
} from "../types";
import {
  REG_STATUS_LABEL,
  REG_STATUS_STYLE,
  ACCOUNT_STATUS_LABEL,
  ACCOUNT_STATUS_STYLE,
  INTEGRATION_STATUS_LABEL,
  INTEGRATION_STATUS_STYLE,
  INSTITUTION_TYPE_LABEL,
  INSTITUTION_TYPE_PILL,
  money,
  formatDate,
} from "../badges";

function StatusBadge({ label, style }: { label: string; style: string }) {
  return <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${style}`}>{label}</span>;
}

const TABS = ["Institution Information", "Students", "Integration Status", "Fee Submissions", "Settlement", "Transactions", "Reconciliation", "Reports"];

const STUDENT_STATUS_STYLE: Record<string, string> = {
  Paid: "bg-green-50 text-green-700 border-green-200",
  Partial: "bg-amber-50 text-amber-700 border-amber-200",
  Unpaid: "bg-red-50 text-red-700 border-red-200",
};

const INTEGRATION_BANNER: Record<string, { dot: string; bg: string; border: string; title: string; titleColor: string }> = {
  INTEGRATED: { dot: "bg-green-500", bg: "bg-green-50", border: "border-green-200", title: "Integration Active", titleColor: "text-green-800" },
  PENDING: { dot: "bg-amber-400", bg: "bg-amber-50", border: "border-amber-200", title: "Setup In Progress", titleColor: "text-amber-800" },
  FAILED: { dot: "bg-red-500", bg: "bg-red-50", border: "border-red-200", title: "Integration Failed", titleColor: "text-red-800" },
  NOT_INTEGRATED: { dot: "bg-gray-400", bg: "bg-gray-50", border: "border-gray-200", title: "Not Connected", titleColor: "text-gray-700" },
};

export default function InstitutionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const apiClient = useApiClient();
  const { show } = useToast();

  const [detail, setDetail] = useState<InstitutionDetailDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState(0);

  const [students, setStudents] = useState<InstitutionStudentDto[] | null>(null);
  const [integration, setIntegration] = useState<InstitutionIntegrationDto | null>(null);
  const [feeSubmissions, setFeeSubmissions] = useState<FeeSubmissionSummaryDto[] | null>(null);
  const [settlements, setSettlements] = useState<InstitutionSettlementsResponse | null>(null);

  const [activateModal, setActivateModal] = useState(false);
  const [deactivateModal, setDeactivateModal] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    apiClient
      .get<InstitutionDetailDto>(`/institutions/${id}`)
      .then(setDetail)
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load institution"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  useEffect(() => {
    if (tab === 1 && students === null) apiClient.get<InstitutionStudentDto[]>(`/institutions/${id}/students`).then(setStudents);
    if (tab === 2 && integration === null) apiClient.get<InstitutionIntegrationDto>(`/institutions/${id}/integration`).then(setIntegration);
    if (tab === 3 && feeSubmissions === null) apiClient.get<FeeSubmissionSummaryDto[]>(`/institutions/${id}/fee-submissions`).then(setFeeSubmissions);
    if (tab === 4 && settlements === null) apiClient.get<InstitutionSettlementsResponse>(`/institutions/${id}/settlements`).then(setSettlements);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, id]);

  async function runActivate() {
    setActionLoading(true);
    try {
      const updated = await apiClient.post<InstitutionDetailDto>(`/institutions/${id}/activate`);
      setDetail(updated);
      show("Account activated successfully.", "success");
    } catch (err) {
      show(err instanceof Error ? err.message : "Activation failed", "error");
    } finally {
      setActionLoading(false);
      setActivateModal(false);
    }
  }

  async function runDeactivate() {
    setActionLoading(true);
    try {
      const updated = await apiClient.post<InstitutionDetailDto>(`/institutions/${id}/deactivate`);
      setDetail(updated);
      show("Account deactivated.", "success");
    } catch (err) {
      show(err instanceof Error ? err.message : "Deactivation failed", "error");
    } finally {
      setActionLoading(false);
      setDeactivateModal(false);
    }
  }

  if (error) return <EmptyState title="Could not load institution" description={error} />;
  if (!detail) {
    return (
      <div className="flex justify-center py-20">
        <LoadingSpinner />
      </div>
    );
  }

  const infoFields: [string, string][] = [
    ["Institution ID", detail.code],
    ["Registration Number", detail.registrationNumber],
    ["Institution Name", detail.name],
    ["Institution Type", INSTITUTION_TYPE_LABEL[detail.institutionType]],
    [detail.institutionType === "UNIVERSITY" ? "University Type" : "School Type", detail.subType],
    ["City", detail.city],
    [detail.institutionType === "UNIVERSITY" ? "Vice Chancellor / President" : "Principal", detail.principalName],
    ["Phone", detail.phone],
    ["Email", detail.email],
    ["Account Status", ACCOUNT_STATUS_LABEL[detail.accountStatus]],
    ["Registration Date", formatDate(detail.registeredAt)],
    [detail.institutionType === "UNIVERSITY" ? "Enrolled Students" : "Total Students", detail.studentCount.toLocaleString()],
  ];

  return (
    <div className="space-y-4">
      <button onClick={() => router.push("/bank/schools")} className="flex items-center gap-1 text-sm text-gray-400 hover:text-gray-700 transition-colors">
        <ChevronLeftIcon className="w-4 h-4" /> Back to Institutions
      </button>

      <div className="bg-white rounded-xl border p-6" style={{ borderColor: "#E8EDF5" }}>
        <div className="flex items-start justify-between flex-wrap gap-3">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 bg-[var(--cib-blue)]/10 rounded-xl flex items-center justify-center">
              <SchoolIcon className="w-7 h-7 text-[var(--cib-blue)]" />
            </div>
            <div>
              <div className="flex items-center gap-2 mb-0.5">
                <h2 className="text-xl font-bold" style={{ color: "var(--cib-text)" }}>
                  {detail.name}
                </h2>
                <span className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold ${INSTITUTION_TYPE_PILL[detail.institutionType]}`}>
                  {INSTITUTION_TYPE_LABEL[detail.institutionType]}
                </span>
              </div>
              <p className="text-sm text-gray-400">
                {detail.code} - {detail.city} - {detail.subType}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2 flex-wrap justify-end">
            <StatusBadge label={REG_STATUS_LABEL[detail.registrationStatus]} style={REG_STATUS_STYLE[detail.registrationStatus]} />
            <StatusBadge label={ACCOUNT_STATUS_LABEL[detail.accountStatus]} style={ACCOUNT_STATUS_STYLE[detail.accountStatus]} />
            {detail.accountStatus === "ACTIVE" && (
              <button onClick={() => setDeactivateModal(true)} className="px-2.5 py-1 text-[11px] font-semibold text-red-600 border border-red-200 rounded hover:bg-red-50 transition-colors">
                Deactivate
              </button>
            )}
            {detail.accountStatus !== "ACTIVE" && detail.registrationStatus === "APPROVED" && (
              <button onClick={() => setActivateModal(true)} className="px-2.5 py-1 text-[11px] font-semibold text-green-700 border border-green-200 rounded hover:bg-green-50 transition-colors">
                Activate
              </button>
            )}
          </div>
        </div>

        <div className="grid grid-cols-4 gap-4 mt-5 pt-5 border-t border-gray-100">
          {[
            { label: "Students", value: detail.studentCount.toLocaleString() },
            { label: "Integration", value: INTEGRATION_STATUS_LABEL[detail.integrationStatus] },
            { label: "Registered", value: formatDate(detail.registeredAt) },
            { label: "Sub-type", value: detail.subType },
          ].map(({ label, value }) => (
            <div key={label}>
              <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1">{label}</div>
              <div className="text-sm font-semibold text-gray-800">{value}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="bg-white rounded-xl border overflow-hidden" style={{ borderColor: "#E8EDF5" }}>
        <div className="flex border-b border-gray-100 overflow-x-auto">
          {TABS.map((t, i) => (
            <button
              key={t}
              onClick={() => setTab(i)}
              className={`px-5 py-3.5 text-xs font-semibold whitespace-nowrap transition-colors border-b-2 ${
                tab === i ? "text-[var(--cib-blue)] border-[var(--cib-blue)] bg-[var(--cib-blue-light)]" : "text-gray-400 border-transparent hover:text-gray-700"
              }`}
            >
              {t}
            </button>
          ))}
        </div>

        <div className="p-5">
          {tab === 0 && (
            <div className="grid grid-cols-2 gap-x-10 gap-y-4">
              {infoFields.map(([k, v]) => (
                <div key={k}>
                  <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-0.5">{k}</div>
                  <div className="text-sm text-gray-800">{v}</div>
                </div>
              ))}
              {detail.rejectionReason && (
                <div className="col-span-2">
                  <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-0.5">Rejection Reason</div>
                  <div className="text-sm text-red-700">{detail.rejectionReason}</div>
                </div>
              )}
            </div>
          )}

          {tab === 1 &&
            (students === null ? (
              <div className="flex justify-center py-10">
                <LoadingSpinner />
              </div>
            ) : students.length === 0 ? (
              <EmptyState title="No students enrolled yet" />
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[#F8FAFD]">
                    {["Name", "Total Fees", "Paid", "Outstanding", "Status"].map((h) => (
                      <th key={h} className="text-left px-4 py-2.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {students.map((s) => (
                    <tr key={s.studentId} className="hover:bg-[#F8FAFD]">
                      <td className="px-4 py-3 text-xs text-gray-800 font-medium">{s.fullName}</td>
                      <td className="px-4 py-3 text-xs font-mono text-gray-600">{money(s.totalFeesEGP)}</td>
                      <td className="px-4 py-3 text-xs font-mono text-gray-600">{money(s.paidEGP)}</td>
                      <td className="px-4 py-3 text-xs font-mono font-semibold text-gray-700">{money(s.outstandingEGP)}</td>
                      <td className="px-4 py-3">
                        <StatusBadge label={s.status} style={STUDENT_STATUS_STYLE[s.status] ?? "bg-gray-50 text-gray-600 border-gray-200"} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ))}

          {tab === 2 &&
            (integration === null ? (
              <div className="flex justify-center py-10">
                <LoadingSpinner />
              </div>
            ) : (
              (() => {
                const cfg = INTEGRATION_BANNER[integration.status];
                return (
                  <div className={`rounded-xl border ${cfg.bg} ${cfg.border} p-5`}>
                    <div className="flex items-center gap-3 mb-2">
                      <span className={`w-3 h-3 rounded-full shrink-0 ${cfg.dot}`} />
                      <h3 className={`text-base font-bold ${cfg.titleColor}`}>{cfg.title}</h3>
                      <StatusBadge label={INTEGRATION_STATUS_LABEL[integration.status]} style={INTEGRATION_STATUS_STYLE[integration.status]} />
                    </div>
                    <p className="text-sm text-gray-600 ml-6">{integration.message}</p>
                  </div>
                );
              })()
            ))}

          {tab === 3 &&
            (feeSubmissions === null ? (
              <div className="flex justify-center py-10">
                <LoadingSpinner />
              </div>
            ) : feeSubmissions.length === 0 ? (
              <div className="text-center py-10 text-gray-400">
                <ReportIcon className="w-8 h-8 mx-auto mb-3 opacity-40" />
                <p className="text-sm font-medium mb-1">No fee submissions yet</p>
                <p className="text-xs">Fee submissions will appear here once the institution uploads a fee file.</p>
              </div>
            ) : (
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-[#F8FAFD]">
                    {["File", "Uploaded", "Rows", "Successful", "Failed", "Status"].map((h) => (
                      <th key={h} className="text-left px-4 py-2.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {feeSubmissions.map((fs) => (
                    <tr key={fs.submissionId} className="hover:bg-[#F8FAFD]">
                      <td className="px-4 py-3 text-xs text-gray-700 font-medium">{fs.fileName}</td>
                      <td className="px-4 py-3 text-xs text-gray-500 font-mono whitespace-nowrap">{new Date(fs.uploadedAt).toLocaleString("en-GB")}</td>
                      <td className="px-4 py-3 text-xs font-mono text-gray-600">{fs.totalRows}</td>
                      <td className="px-4 py-3 text-xs font-mono text-green-700">{fs.successfulRows}</td>
                      <td className="px-4 py-3 text-xs font-mono text-red-600">{fs.failedRows}</td>
                      <td className="px-4 py-3">
                        <StatusBadge
                          label={fs.status}
                          style={
                            fs.status === "PROCESSED"
                              ? "bg-green-50 text-green-700 border-green-200"
                              : fs.status === "PARTIAL"
                              ? "bg-amber-50 text-amber-700 border-amber-200"
                              : "bg-red-50 text-red-700 border-red-200"
                          }
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ))}

          {tab === 4 &&
            (settlements === null ? (
              <div className="flex justify-center py-10">
                <LoadingSpinner />
              </div>
            ) : settlements.data.length === 0 ? (
              <div className="text-center py-10 text-gray-400">
                <ReconcileIcon className="w-8 h-8 mx-auto mb-3 opacity-40" />
                <p className="text-sm font-medium mb-1">No settlement records</p>
                <p className="text-xs">Settlement records appear here once fee submissions have been processed and settled.</p>
              </div>
            ) : (
              <div className="space-y-4">
                <div className="grid grid-cols-3 gap-4">
                  {[
                    { label: "Total Settled", value: money(settlements.summary.totalSettledEGP) },
                    { label: "Settlement Records", value: String(settlements.summary.recordCount) },
                    { label: "Last Settlement", value: formatDate(settlements.summary.lastSettlementDate) },
                  ].map(({ label, value }) => (
                    <div key={label} className="bg-[#F8FAFD] rounded-lg px-4 py-3">
                      <div className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-0.5">{label}</div>
                      <div className="text-sm font-bold text-gray-800">{value}</div>
                    </div>
                  ))}
                </div>
                <table className="w-full text-sm">
                  <thead>
                    <tr className="bg-[#F8FAFD]">
                      {["Settlement ID", "Date", "Gross", "CIB Fee", "Net", "Status", "References"].map((h) => (
                        <th key={h} className="text-left px-4 py-2.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">
                          {h}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {settlements.data.map((rec) => (
                      <tr key={rec.id} className="hover:bg-[#F8FAFD]">
                        <td className="px-4 py-3 font-mono text-xs text-[var(--cib-blue)]">{rec.id}</td>
                        <td className="px-4 py-3 text-xs text-gray-500 font-mono whitespace-nowrap">{formatDate(rec.date)}</td>
                        <td className="px-4 py-3 text-xs font-mono text-gray-700">{money(rec.grossEGP)}</td>
                        <td className="px-4 py-3 text-xs font-mono text-red-600">{money(rec.cibFeeEGP)}</td>
                        <td className="px-4 py-3 text-xs font-mono font-semibold text-gray-800">{money(rec.netEGP)}</td>
                        <td className="px-4 py-3">
                          <StatusBadge label={rec.status} style="bg-green-50 text-green-700 border-green-200" />
                        </td>
                        <td className="px-4 py-3 text-[10px] text-gray-400 font-mono">
                          {rec.txRef} / {rec.reconRef}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}

          {[5, 6, 7].includes(tab) && (
            <div className="text-center py-10 text-gray-400">
              <AlertIcon className="w-8 h-8 mx-auto mb-3 opacity-40" />
              <p className="text-sm">
                {TABS[tab]} for this institution isn&apos;t a separate ticket yet - see{" "}
                <Link href="/bank/transactions" className="text-[var(--cib-blue)] hover:underline">
                  Transactions
                </Link>{" "}
                or{" "}
                <Link href="/bank/reconciliation" className="text-[var(--cib-blue)] hover:underline">
                  Reconciliation
                </Link>{" "}
                for network-wide views.
              </p>
            </div>
          )}
        </div>
      </div>

      {activateModal && (
        <Modal open onClose={() => setActivateModal(false)} title="Confirm Activation">
          <p className="text-sm text-gray-500 mb-5">
            Activate <strong>{detail.name}</strong>? This enables fee collection for this institution.
          </p>
          <div className="flex gap-3">
            <button onClick={() => setActivateModal(false)} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
              Cancel
            </button>
            <button onClick={runActivate} disabled={actionLoading} className="flex-1 py-2.5 text-sm font-semibold text-white bg-[var(--cib-blue)] rounded-lg hover:opacity-90 transition-colors disabled:opacity-50">
              Confirm
            </button>
          </div>
        </Modal>
      )}
      {deactivateModal && (
        <Modal open onClose={() => setDeactivateModal(false)} title="Confirm Deactivation">
          <p className="text-sm text-gray-500 mb-5">
            Deactivate <strong>{detail.name}</strong>? Fee collection will be suspended until the account is reactivated.
          </p>
          <div className="flex gap-3">
            <button onClick={() => setDeactivateModal(false)} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
              Cancel
            </button>
            <button onClick={runDeactivate} disabled={actionLoading} className="flex-1 py-2.5 text-sm font-semibold text-white bg-red-600 rounded-lg hover:bg-red-700 transition-colors disabled:opacity-50">
              Confirm
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
