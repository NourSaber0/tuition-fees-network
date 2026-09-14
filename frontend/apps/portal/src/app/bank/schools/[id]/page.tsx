"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { useApiClient } from "@tuition/api-client";
import { Badge, type BadgeTone, Button, Modal, LoadingSpinner, EmptyState, useToast, ChevronLeftIcon } from "@tuition/ui";
import type {
  InstitutionDetailDto,
  RegistrationStatus,
  AccountStatus,
  IntegrationStatus,
} from "../types";
import { INPUT_CLASSES } from "../formStyles";
import { StudentsTab } from "./StudentsTab";
import { ApplicationTab } from "./ApplicationTab";
import { IntegrationTab } from "./IntegrationTab";
import { FeeSubmissionsTab } from "./FeeSubmissionsTab";
import { SettlementsTab } from "./SettlementsTab";

const REG_STATUS_TONE: Record<RegistrationStatus, BadgeTone> = {
  PENDING: "warning",
  UNDER_REVIEW: "info",
  APPROVED: "success",
  REJECTED: "danger",
};

const ACCOUNT_STATUS_TONE: Record<AccountStatus, BadgeTone> = {
  ACTIVE: "success",
  INACTIVE: "neutral",
  SUSPENDED: "danger",
};

const INTEGRATION_STATUS_TONE: Record<IntegrationStatus, BadgeTone> = {
  NOT_INTEGRATED: "neutral",
  PENDING: "warning",
  INTEGRATED: "success",
  FAILED: "danger",
};

type Tab = "students" | "application" | "integration" | "feeSubmissions" | "settlements";
const TABS: { id: Tab; label: string }[] = [
  { id: "students", label: "Students" },
  { id: "application", label: "Application" },
  { id: "integration", label: "Integration" },
  { id: "feeSubmissions", label: "Fee Submissions" },
  { id: "settlements", label: "Settlements" },
];

type PendingAction = "approve" | "reject" | "activate" | "deactivate" | null;

export default function InstitutionDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const apiClient = useApiClient();
  const toast = useToast();

  const [institution, setInstitution] = useState<InstitutionDetailDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loading = institution === null && error === null;
  const [tab, setTab] = useState<Tab>("students");

  const [pendingAction, setPendingAction] = useState<PendingAction>(null);
  const [reason, setReason] = useState("");
  const [notes, setNotes] = useState("");
  const [actionSubmitting, setActionSubmitting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  function load() {
    apiClient
      .get<InstitutionDetailDto>(`/institutions/${id}`)
      .then((data) => {
        setInstitution(data);
        setError(null);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load institution"));
  }

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  async function runAction() {
    if (!pendingAction) return;
    setActionSubmitting(true);
    setActionError(null);
    try {
      if (pendingAction === "reject") {
        if (!reason.trim()) {
          setActionError("A rejection reason is required.");
          setActionSubmitting(false);
          return;
        }
        const updated = await apiClient.post<InstitutionDetailDto>(`/institutions/${id}/reject`, {
          reason: reason.trim(),
          notes: notes.trim() || undefined,
        });
        setInstitution(updated);
        toast.show("Institution rejected", "info");
      } else {
        const updated = await apiClient.post<InstitutionDetailDto>(`/institutions/${id}/${pendingAction}`, {});
        setInstitution(updated);
        toast.show(
          {
            approve: "Institution approved and activated",
            activate: "Institution activated",
            deactivate: "Institution deactivated",
          }[pendingAction],
          "success"
        );
      }
      setPendingAction(null);
      setReason("");
      setNotes("");
    } catch (err) {
      setActionError(err instanceof Error ? err.message : "Action failed");
    } finally {
      setActionSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="flex justify-center py-20">
        <LoadingSpinner />
      </div>
    );
  }

  if (error || !institution) {
    return <EmptyState title="Could not load this institution" description={error ?? undefined} />;
  }

  const reviewable = institution.registrationStatus === "PENDING" || institution.registrationStatus === "UNDER_REVIEW";
  const canActivate = institution.registrationStatus === "APPROVED" && institution.accountStatus !== "ACTIVE";
  const canDeactivate = institution.accountStatus === "ACTIVE";

  return (
    <div className="space-y-5">
      <Link href="/bank/schools" className="inline-flex items-center gap-1 text-xs font-medium text-[var(--cib-blue)] hover:underline">
        <ChevronLeftIcon className="w-3.5 h-3.5" />
        Back to Institutions
      </Link>

      <div className="bg-white rounded-xl border p-5" style={{ borderColor: "#E8EDF5" }}>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div>
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className="text-lg font-bold" style={{ color: "var(--cib-text)" }}>
                {institution.name}
              </h1>
              <Badge tone={REG_STATUS_TONE[institution.registrationStatus]}>
                {institution.registrationStatus.replace("_", " ")}
              </Badge>
              <Badge tone={ACCOUNT_STATUS_TONE[institution.accountStatus]}>{institution.accountStatus}</Badge>
              <Badge tone={INTEGRATION_STATUS_TONE[institution.integrationStatus]}>
                {institution.integrationStatus.replace("_", " ")}
              </Badge>
            </div>
            <p className="text-xs text-[var(--cib-text-muted)] mt-1">
              {institution.code} - {institution.institutionType === "UNIVERSITY" ? "University" : "School"} - {institution.subType} -{" "}
              {institution.city}
            </p>
            {institution.rejectionReason && (
              <p className="text-xs text-red-600 mt-2 bg-red-50 rounded px-2.5 py-1.5 inline-block">
                Rejected: {institution.rejectionReason}
              </p>
            )}
          </div>

          <div className="flex items-center gap-2">
            {reviewable && (
              <>
                <Button size="sm" variant="danger" onClick={() => setPendingAction("reject")}>
                  Reject
                </Button>
                <Button size="sm" onClick={() => setPendingAction("approve")}>
                  Approve
                </Button>
              </>
            )}
            {canActivate && (
              <Button size="sm" onClick={() => setPendingAction("activate")}>
                Activate
              </Button>
            )}
            {canDeactivate && (
              <Button size="sm" variant="danger" onClick={() => setPendingAction("deactivate")}>
                Deactivate
              </Button>
            )}
          </div>
        </div>

        <dl className="grid grid-cols-4 gap-4 mt-5 pt-4 border-t border-gray-100">
          <Info label="Principal" value={institution.principalName} />
          <Info label="Phone" value={institution.phone} />
          <Info label="Email" value={institution.email} />
          <Info label="Registration Number" value={institution.registrationNumber} />
          <Info label="Students" value={institution.studentCount.toLocaleString()} />
          <Info label="Registered" value={institution.registeredAt} />
          <Info label="Fee Absorption Policy" value={institution.feeAbsorptionPolicy ?? "-"} />
        </dl>
      </div>

      <div className="bg-white rounded-xl border overflow-hidden" style={{ borderColor: "#E8EDF5" }}>
        <div className="flex border-b border-gray-100 px-2">
          {TABS.map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className="px-4 py-3 text-xs font-semibold relative transition-colors"
              style={{ color: tab === t.id ? "var(--cib-blue)" : "var(--cib-text-muted)" }}
            >
              {t.label}
              {tab === t.id && <span className="absolute left-0 right-0 bottom-0 h-0.5" style={{ background: "var(--cib-blue)" }} />}
            </button>
          ))}
        </div>
        <div className="p-5">
          {tab === "students" && <StudentsTab institutionId={id} />}
          {tab === "application" && <ApplicationTab institutionId={id} />}
          {tab === "integration" && <IntegrationTab institutionId={id} />}
          {tab === "feeSubmissions" && <FeeSubmissionsTab institutionId={id} />}
          {tab === "settlements" && <SettlementsTab institutionId={id} />}
        </div>
      </div>

      <Modal
        open={pendingAction !== null}
        onClose={() => {
          if (!actionSubmitting) {
            setPendingAction(null);
            setActionError(null);
            setReason("");
            setNotes("");
          }
        }}
        title={
          {
            approve: "Approve Institution",
            reject: "Reject Institution",
            activate: "Activate Institution",
            deactivate: "Deactivate Institution",
          }[pendingAction ?? "approve"]
        }
        footer={
          <>
            <Button variant="secondary" size="sm" disabled={actionSubmitting} onClick={() => setPendingAction(null)}>
              Cancel
            </Button>
            <Button
              size="sm"
              variant={pendingAction === "reject" || pendingAction === "deactivate" ? "danger" : "primary"}
              loading={actionSubmitting}
              onClick={runAction}
            >
              Confirm
            </Button>
          </>
        }
      >
        {actionError && <p className="text-xs text-red-600 bg-red-50 rounded px-3 py-2 mb-3">{actionError}</p>}
        {pendingAction === "approve" && (
          <p className="text-sm text-[var(--cib-text)]">
            Approve <strong>{institution.name}</strong>? This activates the account and allows fee collection to begin.
          </p>
        )}
        {pendingAction === "activate" && (
          <p className="text-sm text-[var(--cib-text)]">
            Activate <strong>{institution.name}</strong>? This resumes fee collection for this institution.
          </p>
        )}
        {pendingAction === "deactivate" && (
          <p className="text-sm text-[var(--cib-text)]">
            Deactivate <strong>{institution.name}</strong>? This immediately suspends fee collection for this institution.
          </p>
        )}
        {pendingAction === "reject" && (
          <div className="space-y-3">
            <p className="text-sm text-[var(--cib-text)]">
              Reject <strong>{institution.name}</strong>&apos;s application.
            </p>
            <label className="block">
              <span className="text-xs font-medium text-[var(--cib-text-muted)]">
                Reason <span className="text-red-500">*</span>
              </span>
              <textarea
                required
                rows={2}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                className={`${INPUT_CLASSES} mt-1`}
              />
            </label>
            <label className="block">
              <span className="text-xs font-medium text-[var(--cib-text-muted)]">Notes</span>
              <textarea rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} className={`${INPUT_CLASSES} mt-1`} />
            </label>
          </div>
        )}
      </Modal>
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
