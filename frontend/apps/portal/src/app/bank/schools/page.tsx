"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useApiClient } from "@tuition/api-client";
import { LoadingSpinner, EmptyState, Modal, Pagination, useToast, SearchIcon, EyeIcon, PlusIcon } from "@tuition/ui";
import type {
  InstitutionSummaryDto,
  InstitutionType,
  RegistrationStatus,
  AccountStatus,
  PageResponse,
  RegisterInstitutionRequest,
} from "./types";
import {
  REG_STATUS_LABEL,
  REG_STATUS_STYLE,
  ACCOUNT_STATUS_LABEL,
  ACCOUNT_STATUS_STYLE,
  INSTITUTION_TYPE_LABEL,
  INSTITUTION_TYPE_PILL,
} from "./badges";

function StatusBadge({ label, style }: { label: string; style: string }) {
  return <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-semibold border ${style}`}>{label}</span>;
}

const SCHOOL_SUB_TYPES = ["International", "National", "STEM", "Language", "Private"];
const UNI_SUB_TYPES = ["Public", "Private", "International"];
const CITIES = ["Cairo", "Giza", "Alexandria", "New Cairo", "6th October", "Heliopolis", "Mansoura", "Suez", "Ismailia", "Port Said", "Aswan", "Luxor", "Other"];

type RegForm = {
  institutionType: InstitutionType | "";
  name: string;
  registrationNumber: string;
  city: string;
  subType: string;
  principalName: string;
  phone: string;
  email: string;
  studentCount: string;
};
const EMPTY_FORM: RegForm = {
  institutionType: "",
  name: "",
  registrationNumber: "",
  city: "",
  subType: "",
  principalName: "",
  phone: "",
  email: "",
  studentCount: "",
};

function RegisterModal({ onClose, onRegistered }: { onClose: () => void; onRegistered: () => void }) {
  const apiClient = useApiClient();
  const [form, setForm] = useState<RegForm>(EMPTY_FORM);
  const [errors, setErrors] = useState<Partial<Record<keyof RegForm, boolean>>>({});
  const [submitting, setSubmitting] = useState(false);
  const [apiError, setApiError] = useState<string | null>(null);
  const [created, setCreated] = useState<{ id: string; name: string } | null>(null);

  const set = (k: keyof RegForm, v: string) => {
    setForm((f) => ({ ...f, [k]: v, ...(k === "institutionType" ? { subType: "" } : {}) }));
    setErrors((e) => ({ ...e, [k]: false }));
  };

  const subTypes = form.institutionType === "SCHOOL" ? SCHOOL_SUB_TYPES : form.institutionType === "UNIVERSITY" ? UNI_SUB_TYPES : [];

  async function submit() {
    const required: (keyof RegForm)[] = ["institutionType", "name", "registrationNumber", "city", "subType", "principalName", "phone", "email", "studentCount"];
    const errs: Partial<Record<keyof RegForm, boolean>> = {};
    required.forEach((k) => {
      if (!form[k]) errs[k] = true;
    });
    if (Object.keys(errs).length) {
      setErrors(errs);
      return;
    }
    setApiError(null);
    setSubmitting(true);
    try {
      const body: RegisterInstitutionRequest = {
        name: form.name,
        institutionType: form.institutionType as InstitutionType,
        subType: form.subType,
        city: form.city,
        principalName: form.principalName,
        phone: form.phone,
        email: form.email,
        registrationNumber: form.registrationNumber,
        studentCount: parseInt(form.studentCount, 10) || 0,
      };
      const result = await apiClient.post<{ id: string; name: string }>("/institutions", body);
      setCreated(result);
    } catch (err) {
      setApiError(err instanceof Error ? err.message : "Could not register the institution.");
    } finally {
      setSubmitting(false);
    }
  }

  const fc = (k: keyof RegForm) =>
    `w-full border rounded-lg px-3 py-2.5 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[var(--cib-blue)] ${errors[k] ? "border-red-400 bg-red-50" : "border-[var(--cib-border)]"}`;

  if (created) {
    return (
      <Modal open onClose={() => { onRegistered(); onClose(); }} title="Registration Submitted">
        <p className="text-sm text-gray-500 mb-4">
          Registered <strong>{created.name}</strong> with ID <strong className="font-mono text-[var(--cib-blue)]">{created.id.slice(0, 8)}</strong>. Status set to{" "}
          <strong className="text-amber-700">Pending Review</strong>.
        </p>
        <button
          onClick={() => { onRegistered(); onClose(); }}
          className="w-full py-2.5 text-sm font-semibold text-white bg-[var(--cib-blue)] rounded-lg hover:opacity-90 transition-colors"
        >
          Close
        </button>
      </Modal>
    );
  }

  return (
    <Modal open onClose={onClose} title="Register New Institution">
      <div className="space-y-4">
        {apiError && <p className="text-sm text-red-600">{apiError}</p>}
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Institution Type *</label>
            <select value={form.institutionType} onChange={(e) => set("institutionType", e.target.value)} className={fc("institutionType")}>
              <option value="">Select type...</option>
              <option value="SCHOOL">School</option>
              <option value="UNIVERSITY">University</option>
            </select>
          </div>
          <div>
            <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Sub-type *</label>
            <select value={form.subType} onChange={(e) => set("subType", e.target.value)} disabled={!form.institutionType} className={fc("subType")}>
              <option value="">Select sub-type...</option>
              {subTypes.map((s) => (
                <option key={s}>{s}</option>
              ))}
            </select>
          </div>
        </div>
        <div>
          <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Institution Name *</label>
          <input type="text" value={form.name} onChange={(e) => set("name", e.target.value)} className={fc("name")} />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Registration Number *</label>
            <input type="text" value={form.registrationNumber} onChange={(e) => set("registrationNumber", e.target.value)} className={fc("registrationNumber")} />
          </div>
          <div>
            <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">City *</label>
            <select value={form.city} onChange={(e) => set("city", e.target.value)} className={fc("city")}>
              <option value="">Select city...</option>
              {CITIES.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </div>
        </div>
        <div>
          <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">
            {form.institutionType === "UNIVERSITY" ? "Vice Chancellor / President *" : "Principal Name *"}
          </label>
          <input type="text" value={form.principalName} onChange={(e) => set("principalName", e.target.value)} className={fc("principalName")} />
        </div>
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Phone *</label>
            <input type="tel" value={form.phone} onChange={(e) => set("phone", e.target.value)} className={fc("phone")} />
          </div>
          <div>
            <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Email *</label>
            <input type="email" value={form.email} onChange={(e) => set("email", e.target.value)} className={fc("email")} />
          </div>
        </div>
        <div>
          <label className="text-[11px] font-semibold text-gray-400 uppercase tracking-wider mb-1 block">Number of Students *</label>
          <input type="number" min="1" value={form.studentCount} onChange={(e) => set("studentCount", e.target.value)} className={fc("studentCount")} />
        </div>
        <div className="flex gap-3 pt-2">
          <button onClick={onClose} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
            Cancel
          </button>
          <button
            onClick={submit}
            disabled={submitting}
            className="flex-1 py-2.5 text-sm font-semibold text-white bg-[var(--cib-blue)] rounded-lg hover:opacity-90 transition-colors disabled:opacity-50"
          >
            {submitting ? "Registering..." : "Register Institution"}
          </button>
        </div>
      </div>
    </Modal>
  );
}

export default function SchoolsPage() {
  const apiClient = useApiClient();
  const { show } = useToast();

  const [result, setResult] = useState<PageResponse<InstitutionSummaryDto> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [type, setType] = useState<"" | InstitutionType>("");
  const [regStatus, setRegStatus] = useState<"" | RegistrationStatus>("");
  const [accountStatus, setAccountStatus] = useState<"" | AccountStatus>("");
  const [page, setPage] = useState(0);
  const [showRegModal, setShowRegModal] = useState(false);
  const [actionTarget, setActionTarget] = useState<{ inst: InstitutionSummaryDto; action: "activate" | "deactivate" } | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const t = setTimeout(() => setPage(0), 0);
    return () => clearTimeout(t);
  }, [search, type, regStatus, accountStatus]);

  useEffect(() => {
    const params = new URLSearchParams();
    if (search) params.set("search", search);
    if (type) params.set("type", type);
    if (regStatus) params.set("regStatus", regStatus);
    if (accountStatus) params.set("accountStatus", accountStatus);
    params.set("page", String(page));
    params.set("size", "25");

    const handle = setTimeout(() => {
      apiClient
        .get<PageResponse<InstitutionSummaryDto>>(`/institutions?${params.toString()}`)
        .then(setResult)
        .catch((err) => setError(err instanceof Error ? err.message : "Failed to load institutions"));
    }, 250);
    return () => clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search, type, regStatus, accountStatus, page, reloadKey]);

  async function runAction() {
    if (!actionTarget) return;
    setActionLoading(true);
    try {
      await apiClient.post(`/institutions/${actionTarget.inst.id}/${actionTarget.action}`);
      show(`${actionTarget.inst.name} ${actionTarget.action === "activate" ? "activated" : "deactivated"} successfully.`, "success");
      setActionTarget(null);
      setReloadKey((k) => k + 1);
    } catch (err) {
      show(err instanceof Error ? err.message : "Action failed", "error");
    } finally {
      setActionLoading(false);
    }
  }

  if (error) {
    return <EmptyState title="Could not load institutions" description={error} />;
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3 flex-wrap">
        <p className="text-sm text-gray-400">{result ? `${result.total} institution${result.total !== 1 ? "s" : ""} found` : "Loading..."}</p>
        <div className="flex items-center gap-3 flex-wrap">
          <div className="relative">
            <SearchIcon className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-300" />
            <input
              type="text"
              placeholder="Search institutions..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-8 pr-4 py-2 border border-[var(--cib-border)] rounded-lg text-sm placeholder-gray-300 focus:outline-none focus:ring-2 focus:ring-blue-200 focus:border-[var(--cib-blue)] w-52 bg-white"
            />
          </div>
          <select value={type} onChange={(e) => setType(e.target.value as InstitutionType | "")} className="border border-[var(--cib-border)] rounded-lg px-3 py-2 text-sm text-gray-600 bg-white">
            <option value="">All Types</option>
            <option value="SCHOOL">School</option>
            <option value="UNIVERSITY">University</option>
          </select>
          <select value={regStatus} onChange={(e) => setRegStatus(e.target.value as RegistrationStatus | "")} className="border border-[var(--cib-border)] rounded-lg px-3 py-2 text-sm text-gray-600 bg-white">
            <option value="">All Reg. Statuses</option>
            {(Object.keys(REG_STATUS_LABEL) as RegistrationStatus[]).map((s) => (
              <option key={s} value={s}>
                {REG_STATUS_LABEL[s]}
              </option>
            ))}
          </select>
          <select value={accountStatus} onChange={(e) => setAccountStatus(e.target.value as AccountStatus | "")} className="border border-[var(--cib-border)] rounded-lg px-3 py-2 text-sm text-gray-600 bg-white">
            <option value="">All Account Statuses</option>
            {(Object.keys(ACCOUNT_STATUS_LABEL) as AccountStatus[]).map((s) => (
              <option key={s} value={s}>
                {ACCOUNT_STATUS_LABEL[s]}
              </option>
            ))}
          </select>
          <button
            onClick={() => setShowRegModal(true)}
            className="flex items-center gap-1.5 px-3.5 py-2 text-sm font-semibold text-white bg-[var(--cib-blue)] rounded-lg hover:opacity-90 transition-colors"
          >
            <PlusIcon className="w-3.5 h-3.5" /> Register Institution
          </button>
        </div>
      </div>

      <div className="bg-white rounded-xl border overflow-hidden" style={{ borderColor: "#E8EDF5" }}>
        {!result ? (
          <div className="flex justify-center py-16">
            <LoadingSpinner />
          </div>
        ) : result.data.length === 0 ? (
          <EmptyState title="No institutions match these filters" />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="bg-[#F8FAFD] border-b" style={{ borderColor: "#E8EDF5" }}>
                  {["Institution", "Type", "Reg Status", "Students", "Integration", "Account", "Actions"].map((h) => (
                    <th key={h} className="text-left px-5 py-3.5 text-[11px] font-semibold text-gray-400 uppercase tracking-wider whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {result.data.map((inst) => (
                  <tr key={inst.id} className="hover:bg-[#F8FAFD] transition-colors">
                    <td className="px-5 py-4">
                      <div className="font-medium text-gray-800 text-sm">{inst.name}</div>
                      <div className="text-xs text-gray-400 mt-0.5">{inst.code} - {inst.city}</div>
                    </td>
                    <td className="px-5 py-4">
                      <span className={`inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold ${INSTITUTION_TYPE_PILL[inst.institutionType]}`}>
                        {INSTITUTION_TYPE_LABEL[inst.institutionType]}
                      </span>
                    </td>
                    <td className="px-5 py-4">
                      <StatusBadge label={REG_STATUS_LABEL[inst.registrationStatus]} style={REG_STATUS_STYLE[inst.registrationStatus]} />
                    </td>
                    <td className="px-5 py-4 font-mono text-sm font-semibold text-gray-700">{inst.studentCount.toLocaleString()}</td>
                    <td className="px-5 py-4">
                      <StatusBadge label={inst.integrationStatus.replace("_", " ")} style="bg-gray-100 text-gray-600 border-gray-200" />
                    </td>
                    <td className="px-5 py-4">
                      <StatusBadge label={ACCOUNT_STATUS_LABEL[inst.accountStatus]} style={ACCOUNT_STATUS_STYLE[inst.accountStatus]} />
                    </td>
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-1">
                        <Link href={`/bank/schools/${inst.id}`} title="View" className="p-1.5 rounded hover:bg-[var(--cib-blue)]/10 text-[var(--cib-blue)] transition-colors">
                          <EyeIcon className="w-3.5 h-3.5" />
                        </Link>
                        {(inst.registrationStatus === "PENDING" || inst.registrationStatus === "UNDER_REVIEW") && (
                          <Link
                            href={`/bank/schools/${inst.id}/review`}
                            className="px-2 py-1 text-[10px] font-semibold text-white bg-[var(--cib-blue)] rounded hover:opacity-90 transition-colors"
                          >
                            Review
                          </Link>
                        )}
                        {inst.accountStatus === "ACTIVE" ? (
                          <button
                            onClick={() => setActionTarget({ inst, action: "deactivate" })}
                            className="px-2 py-1 text-[10px] font-semibold text-red-600 border border-red-200 rounded hover:bg-red-50 transition-colors"
                          >
                            Deactivate
                          </button>
                        ) : inst.registrationStatus === "APPROVED" ? (
                          <button
                            onClick={() => setActionTarget({ inst, action: "activate" })}
                            className="px-2 py-1 text-[10px] font-semibold text-green-600 border border-green-200 rounded hover:bg-green-50 transition-colors"
                          >
                            Activate
                          </button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {result && result.totalPages > 1 && (
          <div className="flex items-center justify-between px-5 py-3.5 border-t border-gray-100">
            <span className="text-xs text-gray-400">
              Showing page {result.page + 1} of {result.totalPages} ({result.total} total)
            </span>
            <Pagination page={result.page + 1} totalPages={result.totalPages} onPageChange={(p) => setPage(p - 1)} />
          </div>
        )}
      </div>

      {showRegModal && <RegisterModal onClose={() => setShowRegModal(false)} onRegistered={() => setReloadKey((k) => k + 1)} />}

      {actionTarget && (
        <Modal open onClose={() => setActionTarget(null)} title={`Confirm ${actionTarget.action === "activate" ? "Activation" : "Deactivation"}`}>
          <p className="text-sm text-gray-500 mb-5">
            {actionTarget.action === "activate" ? "Activate" : "Deactivate"} <strong>{actionTarget.inst.name}</strong>?{" "}
            {actionTarget.action === "deactivate" && "This suspends fee collection until the account is reactivated."}
          </p>
          <div className="flex gap-3">
            <button onClick={() => setActionTarget(null)} className="flex-1 py-2.5 text-sm font-semibold border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors">
              Cancel
            </button>
            <button
              onClick={runAction}
              disabled={actionLoading}
              className={`flex-1 py-2.5 text-sm font-semibold text-white rounded-lg transition-colors disabled:opacity-50 ${
                actionTarget.action === "deactivate" ? "bg-red-600 hover:bg-red-700" : "bg-[var(--cib-blue)] hover:opacity-90"
              }`}
            >
              Confirm
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
