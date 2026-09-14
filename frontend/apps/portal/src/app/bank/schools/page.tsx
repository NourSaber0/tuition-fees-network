"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useApiClient } from "@tuition/api-client";
import {
  Table,
  type Column,
  Badge,
  type BadgeTone,
  Button,
  Modal,
  Pagination,
  useToast,
  PlusIcon,
  SearchIcon,
} from "@tuition/ui";
import type {
  InstitutionSummaryDto,
  InstitutionType,
  RegistrationStatus,
  AccountStatus,
  RegisterInstitutionRequest,
} from "./types";
import { INPUT_CLASSES } from "./formStyles";

interface InstitutionsPage {
  data: InstitutionSummaryDto[];
  page: number;
  totalPages: number;
  total: number;
}

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

const EMPTY_FORM: RegisterInstitutionRequest = {
  name: "",
  institutionType: "SCHOOL",
  subType: "",
  city: "",
  principalName: "",
  phone: "",
  email: "",
  registrationNumber: "",
  studentCount: 0,
  code: "",
  feeAbsorptionPolicy: "",
};

export default function InstitutionsListPage() {
  const apiClient = useApiClient();
  const router = useRouter();
  const toast = useToast();

  const [result, setResult] = useState<InstitutionsPage | null>(null);
  const [error, setError] = useState<string | null>(null);
  const loading = result === null && error === null;

  const [search, setSearch] = useState("");
  const [searchInput, setSearchInput] = useState("");
  const [type, setType] = useState<InstitutionType | "">("");
  const [regStatus, setRegStatus] = useState<RegistrationStatus | "">("");
  const [page, setPage] = useState(1);

  const [registerOpen, setRegisterOpen] = useState(false);
  const [form, setForm] = useState<RegisterInstitutionRequest>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const load = useCallback(() => {
    const params = new URLSearchParams();
    if (search) params.set("search", search);
    if (type) params.set("type", type);
    if (regStatus) params.set("regStatus", regStatus);
    // InstitutionManagementController's `page` param is 0-based with no client-page normalization
    // (unlike UserManagementController, which does `page > 0 ? page - 1 : 0` internally) - the UI's
    // own `page` state stays 1-based for Pagination/display, so convert only for this request.
    params.set("page", String(page - 1));
    params.set("pageSize", "20");

    apiClient
      .get<InstitutionsPage>(`/institutions?${params.toString()}`)
      .then((data) => {
        setResult(data);
        setError(null);
      })
      .catch((err) => setError(err instanceof Error ? err.message : "Failed to load institutions"));
  }, [apiClient, search, type, regStatus, page]);

  useEffect(() => {
    load();
  }, [load]);

  function submitSearch(e: React.FormEvent) {
    e.preventDefault();
    setPage(1);
    setSearch(searchInput.trim());
  }

  async function submitRegister(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setSubmitting(true);
    try {
      const payload: RegisterInstitutionRequest = {
        ...form,
        code: form.code?.trim() ? form.code.trim() : undefined,
        feeAbsorptionPolicy: form.feeAbsorptionPolicy?.trim() ? form.feeAbsorptionPolicy.trim() : undefined,
      };
      await apiClient.post("/institutions", payload);
      toast.show(`${form.name} registered - pending review`, "success");
      setRegisterOpen(false);
      setForm(EMPTY_FORM);
      setPage(1);
      load();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Failed to register institution");
    } finally {
      setSubmitting(false);
    }
  }

  const columns: Column<InstitutionSummaryDto>[] = [
    { key: "name", header: "Institution", render: (r) => (
        <div>
          <div className="font-semibold">{r.name}</div>
          <div className="text-[11px] text-[var(--cib-text-muted)]">{r.code} - {r.city}</div>
        </div>
      ) },
    { key: "institutionType", header: "Type", render: (r) => (
        <span className="capitalize">{r.institutionType.toLowerCase()}</span>
      ) },
    { key: "studentCount", header: "Students", render: (r) => r.studentCount.toLocaleString() },
    { key: "registrationStatus", header: "Registration", render: (r) => (
        <Badge tone={REG_STATUS_TONE[r.registrationStatus]}>{r.registrationStatus.replace("_", " ")}</Badge>
      ) },
    { key: "accountStatus", header: "Account", render: (r) => (
        <Badge tone={ACCOUNT_STATUS_TONE[r.accountStatus]}>{r.accountStatus}</Badge>
      ) },
    { key: "registeredAt", header: "Registered", render: (r) => r.registeredAt },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-3">
        <form onSubmit={submitSearch} className="flex items-center gap-2 flex-1 max-w-md">
          <div className="relative flex-1">
            <SearchIcon className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-[var(--cib-text-muted)]" />
            <input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Search by name, code, or city"
              className="w-full pl-9 pr-3 py-2 text-sm rounded-md border border-[var(--cib-border)] bg-white"
            />
          </div>
          <Button type="submit" variant="secondary" size="sm">
            Search
          </Button>
        </form>

        <div className="flex items-center gap-2">
          <select
            value={type}
            onChange={(e) => {
              setType(e.target.value as InstitutionType | "");
              setPage(1);
            }}
            className="text-sm rounded-md border border-[var(--cib-border)] bg-white px-2.5 py-2"
          >
            <option value="">All types</option>
            <option value="SCHOOL">School</option>
            <option value="UNIVERSITY">University</option>
          </select>
          <select
            value={regStatus}
            onChange={(e) => {
              setRegStatus(e.target.value as RegistrationStatus | "");
              setPage(1);
            }}
            className="text-sm rounded-md border border-[var(--cib-border)] bg-white px-2.5 py-2"
          >
            <option value="">All statuses</option>
            <option value="PENDING">Pending</option>
            <option value="UNDER_REVIEW">Under Review</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
          </select>
          <Button onClick={() => setRegisterOpen(true)} size="sm">
            <PlusIcon className="w-4 h-4" />
            Register Institution
          </Button>
        </div>
      </div>

      {error ? (
        <div className="bg-white rounded-xl border p-6 text-sm text-red-600" style={{ borderColor: "#E8EDF5" }}>
          {error}
        </div>
      ) : (
        <div className="bg-white rounded-xl border overflow-hidden" style={{ borderColor: "#E8EDF5" }}>
          <Table
            columns={columns}
            rows={result?.data ?? []}
            rowKey={(r) => r.id}
            loading={loading}
            emptyTitle="No institutions found"
            emptyDescription="Try adjusting your search or filters, or register a new institution."
            onRowClick={(r) => router.push(`/bank/schools/${r.id}`)}
          />
          {result && result.totalPages > 1 && (
            <div className="px-5 py-3 border-t border-gray-100">
              <Pagination page={result.page + 1} totalPages={result.totalPages} onPageChange={setPage} />
            </div>
          )}
        </div>
      )}

      <Modal
        open={registerOpen}
        onClose={() => {
          if (!submitting) {
            setRegisterOpen(false);
            setFormError(null);
          }
        }}
        title="Register New Institution"
        footer={
          <>
            <Button variant="secondary" size="sm" disabled={submitting} onClick={() => setRegisterOpen(false)}>
              Cancel
            </Button>
            <Button size="sm" loading={submitting} form="register-institution-form" type="submit">
              Register
            </Button>
          </>
        }
      >
        <form id="register-institution-form" onSubmit={submitRegister} className="space-y-3">
          {formError && <p className="text-xs text-red-600 bg-red-50 rounded px-3 py-2">{formError}</p>}
          <div className="grid grid-cols-2 gap-3">
            <Field label="Institution Name" required>
              <input required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} className={INPUT_CLASSES} />
            </Field>
            <Field label="Type" required>
              <select
                value={form.institutionType}
                onChange={(e) => setForm({ ...form, institutionType: e.target.value as InstitutionType })}
                className={INPUT_CLASSES}
              >
                <option value="SCHOOL">School</option>
                <option value="UNIVERSITY">University</option>
              </select>
            </Field>
            <Field label="Sub-Type" required>
              <input
                required
                placeholder="e.g. International, Public"
                value={form.subType}
                onChange={(e) => setForm({ ...form, subType: e.target.value })}
                className={INPUT_CLASSES}
              />
            </Field>
            <Field label="City" required>
              <input required value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} className={INPUT_CLASSES} />
            </Field>
            <Field label="Principal Name" required>
              <input
                required
                value={form.principalName}
                onChange={(e) => setForm({ ...form, principalName: e.target.value })}
                className={INPUT_CLASSES}
              />
            </Field>
            <Field label="Phone" required>
              <input required value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} className={INPUT_CLASSES} />
            </Field>
            <Field label="Email" required>
              <input
                required
                type="email"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
                className={INPUT_CLASSES}
              />
            </Field>
            <Field label="Registration Number" required>
              <input
                required
                value={form.registrationNumber}
                onChange={(e) => setForm({ ...form, registrationNumber: e.target.value })}
                className={INPUT_CLASSES}
              />
            </Field>
            <Field label="Student Count" required>
              <input
                required
                type="number"
                min={1}
                value={form.studentCount || ""}
                onChange={(e) => setForm({ ...form, studentCount: Number(e.target.value) })}
                className={INPUT_CLASSES}
              />
            </Field>
            <Field label="Network Code">
              <input
                placeholder="Auto-generated if blank"
                value={form.code}
                onChange={(e) => setForm({ ...form, code: e.target.value })}
                className={INPUT_CLASSES}
              />
            </Field>
          </div>
          <Field label="Fee Absorption Policy">
            <input
              value={form.feeAbsorptionPolicy}
              onChange={(e) => setForm({ ...form, feeAbsorptionPolicy: e.target.value })}
              className={INPUT_CLASSES}
            />
          </Field>
        </form>
      </Modal>
    </div>
  );
}

function Field({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-[var(--cib-text-muted)]">
        {label}
        {required && <span className="text-red-500"> *</span>}
      </span>
      <div className="mt-1">{children}</div>
    </label>
  );
}
